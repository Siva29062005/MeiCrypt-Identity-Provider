-- MeiCrypt Identity Platform - Phase 6: OAuth2 Authorization Server core tables
-- Version: V8
-- Description: Backing storage for the /oauth2/authorize + /oauth2/token flow.
--              * oauth_authorization_codes  -> single-use short-lived codes (Module 6.1)
--              * oauth_access_tokens        -> introspectable access token registry
--              * oauth_refresh_tokens       -> OAuth-scoped refresh tokens with rotation
--
-- Every row is partitioned by organization_id so the OAuth engine cannot
-- leak state across tenants even if a client_id collision were engineered.

-- ============================================================================
-- Module 6.1 - authorization_code grant (PKCE mandatory)
-- ============================================================================
CREATE TABLE oauth_authorization_codes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Opaque code returned in the redirect (SHA-256 hash stored at rest).
    code_hash            VARCHAR(128) NOT NULL UNIQUE,

    organization_id      UUID         NOT NULL,
    client_application_id UUID        NOT NULL,
    user_id              UUID         NOT NULL,
    session_id           UUID,

    redirect_uri         VARCHAR(1000) NOT NULL,
    scopes               VARCHAR(1000) NOT NULL,
    -- Space-separated scopes actually granted (subset of the client's registered scopes).

    -- PKCE (RFC 7636) - MANDATORY. code_challenge is stored verbatim so the
    -- token endpoint can recompute it from the client-provided verifier.
    code_challenge        VARCHAR(255) NOT NULL,
    code_challenge_method VARCHAR(10)  NOT NULL DEFAULT 'S256',

    -- Anti-CSRF state / OIDC nonce round-tripped from /authorize.
    state                 VARCHAR(500),
    nonce                 VARCHAR(500),

    issued_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at           TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_oauth_code_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth_code_client
        FOREIGN KEY (client_application_id) REFERENCES client_applications(id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth_code_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT oauth_code_challenge_method_check
        CHECK (code_challenge_method IN ('S256'))
);

CREATE INDEX idx_oauth_codes_client ON oauth_authorization_codes(client_application_id);
CREATE INDEX idx_oauth_codes_user   ON oauth_authorization_codes(user_id);
CREATE INDEX idx_oauth_codes_expiry ON oauth_authorization_codes(expires_at);

COMMENT ON TABLE oauth_authorization_codes IS
    'Single-use OAuth2 authorization codes (Phase 6, Module 6.1). PKCE mandatory.';

-- ============================================================================
-- Access tokens issued by the OAuth engine.
-- We keep both a signed JWT (returned to the client) AND a server-side row so
-- that:
--   * scopes can be enforced independently of any downstream verification
--   * revocation is instantaneous
--   * later introspection endpoint (RFC 7662) can answer "active?" cheaply
-- ============================================================================
CREATE TABLE oauth_access_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    token_hash            VARCHAR(128) NOT NULL UNIQUE,
        -- SHA-256 of the JWT (opaque hash - never store the JWT itself)
    jwt_id                VARCHAR(64)  NOT NULL UNIQUE,
        -- 'jti' claim of the issued JWT - for cross-reference

    organization_id       UUID NOT NULL,
    client_application_id UUID NOT NULL,
    user_id               UUID NOT NULL,
    session_id            UUID,

    scopes                VARCHAR(1000) NOT NULL,
    audience              VARCHAR(500),

    issued_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at            TIMESTAMP WITH TIME ZONE,
    revoked_reason        VARCHAR(200),

    CONSTRAINT fk_oauth_access_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth_access_client
        FOREIGN KEY (client_application_id) REFERENCES client_applications(id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth_access_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_oauth_access_client   ON oauth_access_tokens(client_application_id);
CREATE INDEX idx_oauth_access_user     ON oauth_access_tokens(user_id);
CREATE INDEX idx_oauth_access_expiry   ON oauth_access_tokens(expires_at);

COMMENT ON TABLE oauth_access_tokens IS
    'Registry of OAuth2 access tokens issued by the platform (Phase 6, Module 6.2)';

-- ============================================================================
-- OAuth refresh tokens - independent of Phase 3 user-session refresh tokens.
-- Rotation & reuse detection mirror the Phase 3 semantics.
-- ============================================================================
CREATE TABLE oauth_refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    token_hash            VARCHAR(128) NOT NULL UNIQUE,

    organization_id       UUID NOT NULL,
    client_application_id UUID NOT NULL,
    user_id               UUID NOT NULL,
    session_id            UUID,

    scopes                VARCHAR(1000) NOT NULL,

    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
        -- ACTIVE, ROTATED, REVOKED, EXPIRED, COMPROMISED
    parent_token_hash     VARCHAR(128),

    issued_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at            TIMESTAMP WITH TIME ZONE,
    revoked_reason        VARCHAR(200),

    CONSTRAINT fk_oauth_refresh_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth_refresh_client
        FOREIGN KEY (client_application_id) REFERENCES client_applications(id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth_refresh_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT oauth_refresh_status_check
        CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED', 'COMPROMISED'))
);

CREATE INDEX idx_oauth_refresh_client ON oauth_refresh_tokens(client_application_id);
CREATE INDEX idx_oauth_refresh_user   ON oauth_refresh_tokens(user_id);
CREATE INDEX idx_oauth_refresh_status ON oauth_refresh_tokens(status);

COMMENT ON TABLE oauth_refresh_tokens IS
    'OAuth2 refresh tokens with rotation + reuse detection (Phase 6, Module 6.1)';
