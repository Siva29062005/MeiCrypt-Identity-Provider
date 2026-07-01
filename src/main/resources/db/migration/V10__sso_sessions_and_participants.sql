-- MeiCrypt Identity Platform - Phase 8: SSO Federation
-- Version: V10
-- Description: Persistent SSO session tracking + participant client registry so a
--              single Phase-3 user session can transparently seed multiple OAuth
--              client applications (SSO shared sessions, Module 8.1) and be torn
--              down atomically at logout (Single Logout, Module 8.2).
--
-- Design contract:
--   * One SSO session per (user_session) - i.e. a strict 1:1 with Phase-3.
--   * A `participant` row is written the first time each client_application
--     obtains a token through this SSO session; subsequent authorize calls
--     from the same client are silent (no re-prompt) unless prompt=login.
--   * On logout every participant is revoked and (if the client registered
--     a back-channel URL) queued for RP notification.

-- ============================================================================
-- Module 8.1 - SSO sessions
-- ============================================================================
CREATE TABLE sso_sessions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- The Phase-3 user_session that anchors this SSO context.
    user_session_id     UUID        NOT NULL UNIQUE,
    user_id             UUID        NOT NULL,
    organization_id     UUID        NOT NULL,

    -- Opaque, browser-cookie-bound identifier used by /oauth2/authorize to
    -- decide whether the caller can silently reuse the current login.
    sso_id              VARCHAR(64) NOT NULL UNIQUE,

    -- Original auth context: point-in-time login evidence for downstream
    -- audit + Back-Channel Logout claims (OIDC RP-Initiated Logout §5).
    authenticated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address          VARCHAR(45),
    user_agent          VARCHAR(500),

    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    terminated_at       TIMESTAMP WITH TIME ZONE,
    termination_reason  VARCHAR(200),

    CONSTRAINT fk_sso_session_user_session
        FOREIGN KEY (user_session_id) REFERENCES user_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_sso_session_user
        FOREIGN KEY (user_id)         REFERENCES users(id)         ON DELETE CASCADE,
    CONSTRAINT fk_sso_session_org
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT sso_sessions_status_check
        CHECK (status IN ('ACTIVE', 'TERMINATED', 'EXPIRED'))
);

CREATE INDEX idx_sso_sessions_user   ON sso_sessions(user_id);
CREATE INDEX idx_sso_sessions_org    ON sso_sessions(organization_id);
CREATE INDEX idx_sso_sessions_status ON sso_sessions(status);
CREATE INDEX idx_sso_sessions_expiry ON sso_sessions(expires_at);

COMMENT ON TABLE sso_sessions IS
    'One row per authenticated browser session participating in SSO (Phase 8, Module 8.1)';

-- ============================================================================
-- Module 8.2 - SSO participants (per client) for Single Logout fan-out
-- ============================================================================
CREATE TABLE sso_session_participants (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    sso_session_id              UUID NOT NULL,
    client_application_id       UUID NOT NULL,

    -- Snapshot of the last OAuth code / token exchange for this pair.
    first_authorized_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_authorized_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_scope                  VARCHAR(1000),

    -- Back-channel logout state (queued -> sent | failed).
    logout_notified_at          TIMESTAMP WITH TIME ZONE,
    logout_notification_state   VARCHAR(20),
    logout_notification_error   VARCHAR(500),

    CONSTRAINT fk_sso_participant_sso
        FOREIGN KEY (sso_session_id)        REFERENCES sso_sessions(id)         ON DELETE CASCADE,
    CONSTRAINT fk_sso_participant_client
        FOREIGN KEY (client_application_id) REFERENCES client_applications(id)  ON DELETE CASCADE,
    CONSTRAINT unique_sso_participant
        UNIQUE (sso_session_id, client_application_id),
    CONSTRAINT sso_participant_logout_state_check
        CHECK (logout_notification_state IS NULL
               OR logout_notification_state IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_sso_participant_sso    ON sso_session_participants(sso_session_id);
CREATE INDEX idx_sso_participant_client ON sso_session_participants(client_application_id);

COMMENT ON TABLE sso_session_participants IS
    'Client applications that obtained tokens through an SSO session (Phase 8, Module 8.2)';

-- ============================================================================
-- Client back-channel logout URI (opt-in per client_application).
-- OIDC Back-Channel Logout 1.0 §2.5. Stored as a nullable column on the client
-- to keep the surface minimal - a client may still participate in Front-
-- Channel-only logout (post_logout_redirect_uri) without setting this.
-- ============================================================================
ALTER TABLE client_applications
    ADD COLUMN backchannel_logout_uri VARCHAR(1000);

COMMENT ON COLUMN client_applications.backchannel_logout_uri IS
    'Optional OIDC Back-Channel Logout endpoint (Phase 8, Module 8.2)';
