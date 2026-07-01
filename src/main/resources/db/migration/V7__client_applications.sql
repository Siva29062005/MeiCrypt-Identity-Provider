-- MeiCrypt Identity Platform - Client Application Registry
-- Version: V7
-- Description: Phase 5 - OAuth client applications registered by organizations.
--              Modules 5.1 (Application Discovery) and 5.2 (Credential Issuance).

-- ============================================================================
-- Module 5.1 - Application Discovery
-- Each organization can register multiple client applications (CRM, ERP, etc.).
-- These serve as OAuth2 clients in later phases (6/7).
-- ============================================================================
CREATE TABLE client_applications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL,

    -- Human-facing metadata
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    logo_url VARCHAR(500),
    homepage_url VARCHAR(500),

    -- Classification
    application_type VARCHAR(20) NOT NULL DEFAULT 'WEB',
        -- WEB (confidential server-side), SPA (public browser), MOBILE (public), SERVICE (m2m)

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
        -- ACTIVE, SUSPENDED, REVOKED

    -- Module 5.2 - Credential Issuance
    -- client_id is the PUBLIC identifier (URL-safe, opaque, indexed).
    -- client_secret_hash stores a BCrypt hash - the plaintext is only returned
    -- once at creation / rotation time.
    client_id VARCHAR(64) NOT NULL UNIQUE,
    client_secret_hash VARCHAR(255),
        -- NULL for public clients (SPA/MOBILE) that cannot keep a secret
    client_secret_last_rotated_at TIMESTAMP WITH TIME ZONE,

    -- OAuth wiring (used in Phase 6/7; stored here so credentials & metadata
    -- travel together).
    grant_types VARCHAR(300) NOT NULL DEFAULT 'authorization_code,refresh_token',
        -- comma-separated: authorization_code, refresh_token, client_credentials
    scopes VARCHAR(500) NOT NULL DEFAULT 'openid,profile,email',
        -- space or comma separated scopes
    require_pkce BOOLEAN NOT NULL DEFAULT TRUE,
    require_consent BOOLEAN NOT NULL DEFAULT TRUE,

    access_token_ttl_seconds INTEGER NOT NULL DEFAULT 900,      -- 15 min
    refresh_token_ttl_seconds INTEGER NOT NULL DEFAULT 1209600, -- 14 days

    -- Auditing
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id UUID,

    CONSTRAINT fk_client_applications_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT client_applications_type_check
        CHECK (application_type IN ('WEB', 'SPA', 'MOBILE', 'SERVICE')),
    CONSTRAINT client_applications_status_check
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT unique_client_slug_per_org
        UNIQUE (organization_id, slug)
);

CREATE INDEX idx_client_applications_organization_id ON client_applications(organization_id);
CREATE INDEX idx_client_applications_client_id ON client_applications(client_id);
CREATE INDEX idx_client_applications_status ON client_applications(status);
CREATE INDEX idx_client_applications_type ON client_applications(application_type);

CREATE TRIGGER update_client_applications_updated_at
    BEFORE UPDATE ON client_applications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE client_applications IS
    'OAuth2/OIDC client applications registered by an organization (Phase 5)';

-- ============================================================================
-- Redirect URIs - a client may declare multiple callback endpoints.
-- Stored in a dedicated table to keep validation (exact-match) simple.
-- ============================================================================
CREATE TABLE client_application_redirect_uris (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_application_id UUID NOT NULL,
    redirect_uri VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_car_client_application
        FOREIGN KEY (client_application_id) REFERENCES client_applications(id) ON DELETE CASCADE,
    CONSTRAINT unique_redirect_uri_per_client
        UNIQUE (client_application_id, redirect_uri)
);

CREATE INDEX idx_car_client_application_id
    ON client_application_redirect_uris(client_application_id);

COMMENT ON TABLE client_application_redirect_uris IS
    'Allowed OAuth2 redirect URIs per client application (exact-match validated)';

-- ============================================================================
-- Post-logout redirect URIs - RP-initiated logout targets (OIDC).
-- ============================================================================
CREATE TABLE client_application_logout_uris (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_application_id UUID NOT NULL,
    logout_uri VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cal_client_application
        FOREIGN KEY (client_application_id) REFERENCES client_applications(id) ON DELETE CASCADE,
    CONSTRAINT unique_logout_uri_per_client
        UNIQUE (client_application_id, logout_uri)
);

CREATE INDEX idx_cal_client_application_id
    ON client_application_logout_uris(client_application_id);

COMMENT ON TABLE client_application_logout_uris IS
    'Allowed OIDC post-logout redirect URIs per client application';
