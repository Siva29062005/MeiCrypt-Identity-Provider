-- MeiCrypt Identity Platform - Phase 10 & 11
-- Version: V12
-- Description:
--   Phase 10 (Module 10.1): Asynchronous notification delivery infrastructure -
--                           a durable outbox / queue table for email & SMS dispatch
--                           and reusable named templates.
--   Phase 11 (Module 11.1): Structured, append-only audit trail for every
--                           security-sensitive event across the platform
--                           (IP, actor, action, target, status).
--
-- Design contracts:
--   * `notification_templates` are seeded per-channel and per-key; content bodies
--     are Handlebars-style placeholders resolved at dispatch time. They are
--     never mutated by user input at runtime (managed via migrations only).
--   * `notifications` acts as a persistent outbox. A background worker picks up
--     rows in status PENDING, transitions them to SENDING, then SENT / FAILED
--     with attempt counters, guaranteeing at-least-once semantics.
--   * `audit_events` is INSERT-only. There is NO update / delete path from
--     application code - every downstream aggregation must copy first. The
--     table is partitioned on `occurred_at` (monthly) by the operator; the
--     initial schema below leaves partitioning for infra to attach later.

-- =====================================================================
-- Phase 10 - Notification Templates
-- =====================================================================
CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_key VARCHAR(150) NOT NULL,
    channel VARCHAR(20) NOT NULL,               -- EMAIL | SMS
    locale VARCHAR(10) NOT NULL DEFAULT 'en',
    subject VARCHAR(500),                       -- NULL for SMS
    body_template TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT notification_templates_channel_check
        CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT unique_template_key_channel_locale
        UNIQUE (template_key, channel, locale)
);

CREATE INDEX idx_notification_templates_key       ON notification_templates(template_key);
CREATE INDEX idx_notification_templates_channel   ON notification_templates(channel);

CREATE TRIGGER update_notification_templates_updated_at
    BEFORE UPDATE ON notification_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE notification_templates
    IS 'Reusable named templates (Handlebars-style) resolved at dispatch time.';

-- =====================================================================
-- Phase 10 - Notification Outbox
-- =====================================================================
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID,                       -- NULL for platform-level notifs
    user_id UUID,                               -- Optional target user
    channel VARCHAR(20) NOT NULL,
    template_key VARCHAR(150),                  -- Optional template pointer
    recipient VARCHAR(320) NOT NULL,            -- email address or E.164 phone
    subject VARCHAR(500),
    body TEXT NOT NULL,                         -- Fully rendered payload
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT notifications_channel_check
        CHECK (channel IN ('EMAIL', 'SMS')),
    CONSTRAINT notifications_status_check
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT fk_notifications_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE SET NULL
);

CREATE INDEX idx_notifications_status_scheduled
    ON notifications(status, scheduled_at);
CREATE INDEX idx_notifications_organization_id
    ON notifications(organization_id);
CREATE INDEX idx_notifications_user_id
    ON notifications(user_id);

CREATE TRIGGER update_notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE notifications
    IS 'Durable outbox for asynchronous email / SMS delivery (at-least-once).';

-- =====================================================================
-- Phase 11 - Structured Audit Trail (append-only)
-- =====================================================================
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID,                       -- NULL for platform-level events
    actor_user_id UUID,                         -- NULL for anonymous / system
    actor_email VARCHAR(320),
    actor_type VARCHAR(30) NOT NULL DEFAULT 'USER',
    action VARCHAR(150) NOT NULL,               -- e.g. USER_LOGIN_SUCCESS
    resource_type VARCHAR(80),                  -- e.g. USER, ROLE, APPLICATION
    resource_id VARCHAR(150),
    status VARCHAR(20) NOT NULL,                -- SUCCESS | FAILURE
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    request_id VARCHAR(80),                     -- correlation id from MDC
    metadata JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT audit_events_actor_type_check
        CHECK (actor_type IN ('USER', 'SYSTEM', 'CLIENT_APPLICATION', 'ANONYMOUS')),
    CONSTRAINT audit_events_status_check
        CHECK (status IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_audit_events_org_occurred    ON audit_events(organization_id, occurred_at DESC);
CREATE INDEX idx_audit_events_actor_occurred  ON audit_events(actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_events_action          ON audit_events(action);
CREATE INDEX idx_audit_events_occurred_at     ON audit_events(occurred_at DESC);

COMMENT ON TABLE audit_events
    IS 'Append-only structured audit trail (Phase 11 - Module 11.1).';

-- =====================================================================
-- Seed core templates. These are the minimum set the platform relies on
-- (verification, password reset, org invitation). Additional templates
-- can be added by later migrations.
-- =====================================================================
INSERT INTO notification_templates (template_key, channel, locale, subject, body_template) VALUES
    ('user.verification.email',   'EMAIL', 'en',
     'Verify your MeiCrypt account',
     'Hi {{firstName}},\n\nPlease verify your email by using the code: {{code}}\nThis code expires in {{ttlMinutes}} minutes.\n\n- MeiCrypt Identity'),
    ('user.password.reset.email', 'EMAIL', 'en',
     'Reset your MeiCrypt password',
     'Hi {{firstName}},\n\nWe received a request to reset your password. Use token {{token}} within {{ttlMinutes}} minutes.\n\nIf you did not request this, you can safely ignore this email.\n\n- MeiCrypt Identity'),
    ('organization.invitation.email', 'EMAIL', 'en',
     'You have been invited to {{organizationName}}',
     'Hi,\n\n{{inviterName}} has invited you to join {{organizationName}} on MeiCrypt.\nAccept your invitation: {{acceptUrl}}\n\nThis invitation expires on {{expiresAt}}.\n\n- MeiCrypt Identity');
