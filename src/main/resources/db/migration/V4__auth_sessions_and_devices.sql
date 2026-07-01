-- MeiCrypt Identity Platform - Authentication & Session Management
-- Version: V4
-- Description: Phase 3 - Refresh tokens, sessions and device tracking tables

-- Refresh Tokens (rotating with reuse detection)
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    session_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    parent_token_hash VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_reason VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_org
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_status_check
        CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED', 'COMPROMISED'))
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_session_id ON refresh_tokens(session_id);
CREATE INDEX idx_refresh_tokens_status ON refresh_tokens(status);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- User Sessions (persistent record; Redis holds live cache)
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    device_id UUID,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    terminated_at TIMESTAMP WITH TIME ZONE,
    termination_reason VARCHAR(100),
    CONSTRAINT fk_user_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_sessions_org
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT user_sessions_status_check
        CHECK (status IN ('ACTIVE', 'TERMINATED', 'EXPIRED'))
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_org_id ON user_sessions(organization_id);
CREATE INDEX idx_user_sessions_status ON user_sessions(status);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);

-- User Devices (browser/client fingerprints for audit)
CREATE TABLE user_devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    device_fingerprint VARCHAR(255) NOT NULL,
    device_name VARCHAR(255),
    device_type VARCHAR(50),
    browser VARCHAR(100),
    operating_system VARCHAR(100),
    last_ip_address VARCHAR(45),
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trusted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_devices_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_device_fingerprint
        UNIQUE (user_id, device_fingerprint)
);

CREATE INDEX idx_user_devices_user_id ON user_devices(user_id);
CREATE INDEX idx_user_devices_last_seen_at ON user_devices(last_seen_at);

COMMENT ON TABLE refresh_tokens IS 'Rotating refresh tokens with reuse detection';
COMMENT ON TABLE user_sessions IS 'Persistent record of user login sessions';
COMMENT ON TABLE user_devices IS 'Browser/client device fingerprints per user';
