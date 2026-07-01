-- MeiCrypt Identity Platform - Phase 9: Advanced Multi-Factor Authentication
-- Version: V11
-- Description: Persistent MFA factor catalog per user (TOTP authenticator apps,
--              WebAuthn/Passkey credentials) + short-lived step-up challenges
--              exchanged during the Phase-3 login flow.
--
-- Design contract:
--   * A `user_mfa_factors` row is the abstract "second factor" bound to a user.
--     Its concrete data lives in a sibling type-specific table (TOTP or WebAuthn)
--     linked by factor_id.  Deleting the factor cascades the concrete rows.
--   * Login issues an `mfa_challenges` row with an opaque token whenever the user
--     has at least one ACTIVE factor.  The client redeems that token via
--     /api/v1/mfa/challenges/{id}/verify to complete authentication.
--   * All raw secrets (TOTP seed, WebAuthn attestation blobs) are stored so a
--     KMS-envelope-encrypted column can drop in later without a schema change.
--
-- Module map:
--   9.1 -> totp_enrollments   (Google Authenticator / Authy / 1Password TOTP)
--   9.2 -> webauthn_credentials + webauthn_challenges (Passkeys / security keys)

-- ============================================================================
-- Module 9.1 + 9.2 - Abstract MFA factor per user
-- ============================================================================
CREATE TABLE user_mfa_factors (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID        NOT NULL,
    organization_id     UUID        NOT NULL,

    factor_type         VARCHAR(20) NOT NULL,   -- TOTP | WEBAUTHN
    display_name        VARCHAR(120) NOT NULL,

    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_primary          BOOLEAN     NOT NULL DEFAULT FALSE,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at        TIMESTAMP WITH TIME ZONE,
    last_used_at        TIMESTAMP WITH TIME ZONE,
    revoked_at          TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_mfa_factor_user
        FOREIGN KEY (user_id)         REFERENCES users(id)         ON DELETE CASCADE,
    CONSTRAINT fk_mfa_factor_org
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT user_mfa_factors_type_check
        CHECK (factor_type IN ('TOTP', 'WEBAUTHN')),
    CONSTRAINT user_mfa_factors_status_check
        CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_user_mfa_factors_user   ON user_mfa_factors(user_id);
CREATE INDEX idx_user_mfa_factors_status ON user_mfa_factors(status);
CREATE INDEX idx_user_mfa_factors_type   ON user_mfa_factors(factor_type);

COMMENT ON TABLE user_mfa_factors IS
    'Abstract MFA factor (Phase 9 - Module 9.1 TOTP, Module 9.2 WebAuthn)';

-- ============================================================================
-- Module 9.1 - TOTP enrollments (RFC 6238)
-- ============================================================================
CREATE TABLE totp_enrollments (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    factor_id           UUID        NOT NULL UNIQUE,

    -- Base32-encoded shared secret consumed by the authenticator app.
    secret_base32       VARCHAR(128) NOT NULL,
    algorithm           VARCHAR(10)  NOT NULL DEFAULT 'SHA1',
    digits              INTEGER      NOT NULL DEFAULT 6,
    period_seconds      INTEGER      NOT NULL DEFAULT 30,

    -- Issuer / account label rendered inside the QR otpauth:// URI.
    issuer              VARCHAR(120) NOT NULL,
    account_label       VARCHAR(255) NOT NULL,

    -- Anti-replay: last accepted 30-second window index.
    last_used_counter   BIGINT,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_totp_factor
        FOREIGN KEY (factor_id) REFERENCES user_mfa_factors(id) ON DELETE CASCADE,
    CONSTRAINT totp_enrollments_algo_check
        CHECK (algorithm IN ('SHA1', 'SHA256', 'SHA512')),
    CONSTRAINT totp_enrollments_digits_check
        CHECK (digits BETWEEN 6 AND 10),
    CONSTRAINT totp_enrollments_period_check
        CHECK (period_seconds BETWEEN 15 AND 120)
);

CREATE INDEX idx_totp_enrollments_factor ON totp_enrollments(factor_id);

COMMENT ON TABLE totp_enrollments IS
    'Per-factor TOTP shared secret + config (Phase 9, Module 9.1)';

-- ============================================================================
-- Module 9.2 - WebAuthn credentials
-- ============================================================================
CREATE TABLE webauthn_credentials (
    id                       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    factor_id                UUID        NOT NULL UNIQUE,

    -- Base64url-encoded credential id returned by the authenticator.
    credential_id            VARCHAR(500) NOT NULL UNIQUE,

    -- Base64-encoded COSE_Key + attestation statement + counters.
    attestation_object_b64   TEXT        NOT NULL,
    public_key_cose_b64      TEXT        NOT NULL,

    aaguid                   VARCHAR(64),
    sign_count               BIGINT      NOT NULL DEFAULT 0,

    transports               VARCHAR(200),   -- comma-separated USB,NFC,BLE,INTERNAL
    user_verified            BOOLEAN     NOT NULL DEFAULT FALSE,
    backup_eligible          BOOLEAN     NOT NULL DEFAULT FALSE,
    backup_state             BOOLEAN     NOT NULL DEFAULT FALSE,

    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at             TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_webauthn_factor
        FOREIGN KEY (factor_id) REFERENCES user_mfa_factors(id) ON DELETE CASCADE
);

CREATE INDEX idx_webauthn_credentials_factor        ON webauthn_credentials(factor_id);
CREATE INDEX idx_webauthn_credentials_credential_id ON webauthn_credentials(credential_id);

COMMENT ON TABLE webauthn_credentials IS
    'Registered WebAuthn/Passkey credentials (Phase 9, Module 9.2)';

-- ============================================================================
-- Module 9.2 - WebAuthn registration/assertion challenges (short-lived, opaque)
-- ============================================================================
CREATE TABLE webauthn_challenges (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID        NOT NULL,

    challenge_b64       VARCHAR(200) NOT NULL,
    challenge_type      VARCHAR(20)  NOT NULL,   -- REGISTRATION | ASSERTION
    relying_party_id    VARCHAR(255) NOT NULL,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at         TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_webauthn_challenge_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT webauthn_challenges_type_check
        CHECK (challenge_type IN ('REGISTRATION', 'ASSERTION'))
);

CREATE INDEX idx_webauthn_challenges_user      ON webauthn_challenges(user_id);
CREATE INDEX idx_webauthn_challenges_challenge ON webauthn_challenges(challenge_b64);
CREATE INDEX idx_webauthn_challenges_expiry    ON webauthn_challenges(expires_at);

COMMENT ON TABLE webauthn_challenges IS
    'One-shot server-issued WebAuthn challenges (Phase 9, Module 9.2)';

-- ============================================================================
-- Cross-module - Login-time step-up challenge tracker
-- ============================================================================
CREATE TABLE mfa_challenges (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID        NOT NULL,
    organization_id     UUID        NOT NULL,

    -- Opaque token returned to the client instead of a session; the client
    -- exchanges it (plus a proof from any registered factor) for real tokens.
    challenge_token     VARCHAR(128) NOT NULL UNIQUE,

    -- Which factors are eligible for satisfying this challenge.
    allowed_factor_types VARCHAR(100) NOT NULL,

    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    satisfied_factor_id UUID,

    ip_address          VARCHAR(45),
    user_agent          VARCHAR(500),
    device_fingerprint  VARCHAR(255),
    device_name         VARCHAR(255),

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    satisfied_at        TIMESTAMP WITH TIME ZONE,

    attempts            INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT fk_mfa_challenge_user
        FOREIGN KEY (user_id)         REFERENCES users(id)         ON DELETE CASCADE,
    CONSTRAINT fk_mfa_challenge_org
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT mfa_challenges_status_check
        CHECK (status IN ('PENDING', 'SATISFIED', 'EXPIRED', 'FAILED'))
);

CREATE INDEX idx_mfa_challenges_user   ON mfa_challenges(user_id);
CREATE INDEX idx_mfa_challenges_token  ON mfa_challenges(challenge_token);
CREATE INDEX idx_mfa_challenges_expiry ON mfa_challenges(expires_at);

COMMENT ON TABLE mfa_challenges IS
    'Server-side step-up state issued between password verification and token issuance (Phase 9)';
