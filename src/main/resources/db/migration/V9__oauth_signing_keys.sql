-- MeiCrypt Identity Platform - Phase 7: OIDC Discovery + JWKS
-- Version: V9
-- Description: Persistent RSA signing key registry for the OAuth/OIDC engine.
--              Public keys are exposed at /.well-known/jwks.json so relying
--              parties can verify RS256-signed access_token / id_token JWTs.
--
-- Multiple key entries can exist simultaneously to support seamless rotation:
--   * exactly one row has status = 'ACTIVE'   -> used to sign new tokens
--   * zero or more     status = 'ROTATED'     -> retained so pre-rotation
--                                                tokens still verify
--   * others           status = 'REVOKED'     -> hard-disabled, excluded from JWKS

CREATE TABLE oauth_signing_keys (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- 'kid' claim advertised in the JWKS document and every JWT header.
    kid                     VARCHAR(64) NOT NULL UNIQUE,

    algorithm               VARCHAR(20) NOT NULL DEFAULT 'RS256',
    key_type                VARCHAR(10) NOT NULL DEFAULT 'RSA',
    key_use                 VARCHAR(10) NOT NULL DEFAULT 'sig',

    -- PKCS#8 encoded private key, base64 (safe for column storage). In
    -- production this should be sourced from a KMS/HSM instead of a DB row -
    -- MeiCrypt keeps the row so local/test environments stay self-contained.
    private_key_pkcs8_b64   TEXT        NOT NULL,

    -- X.509 SubjectPublicKeyInfo, base64. Consumed by /.well-known/jwks.json.
    public_key_x509_b64     TEXT        NOT NULL,

    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rotated_at              TIMESTAMP WITH TIME ZONE,
    revoked_at              TIMESTAMP WITH TIME ZONE,

    CONSTRAINT oauth_signing_keys_algorithm_check
        CHECK (algorithm IN ('RS256')),
    CONSTRAINT oauth_signing_keys_status_check
        CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED'))
);

CREATE INDEX idx_oauth_signing_keys_status ON oauth_signing_keys(status);

COMMENT ON TABLE oauth_signing_keys IS
    'RSA signing key registry for the OAuth/OIDC engine (Phase 7, Module 7.2)';
