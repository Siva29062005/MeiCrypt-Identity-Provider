-- MeiCrypt Identity Platform - Initial Schema
-- Version: V1
-- Description: Baseline schema with organization and audit infrastructure

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Organizations Table (Multi-Tenant Anchor)
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT organizations_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'))
);

-- Organization Settings Table
CREATE TABLE organization_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL,
    brand_name VARCHAR(255),
    brand_logo_url VARCHAR(500),
    primary_timezone VARCHAR(50) DEFAULT 'UTC',
    primary_language VARCHAR(10) DEFAULT 'en',
    password_min_length INTEGER DEFAULT 12,
    password_require_uppercase BOOLEAN DEFAULT TRUE,
    password_require_lowercase BOOLEAN DEFAULT TRUE,
    password_require_numbers BOOLEAN DEFAULT TRUE,
    password_require_special_chars BOOLEAN DEFAULT TRUE,
    max_session_duration_minutes INTEGER DEFAULT 480,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_organization_settings_organization 
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

-- Indexes for Organizations
CREATE INDEX idx_organizations_slug ON organizations(slug);
CREATE INDEX idx_organizations_status ON organizations(status);
CREATE INDEX idx_organization_settings_organization_id ON organization_settings(organization_id);

-- Audit Log Table (Immutable Event Store)
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID,
    user_id UUID,
    event_type VARCHAR(100) NOT NULL,
    event_category VARCHAR(50) NOT NULL,
    event_status VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    resource_type VARCHAR(100),
    resource_id VARCHAR(100),
    description TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_audit_logs_organization 
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE SET NULL,
    CONSTRAINT audit_logs_event_status_check 
        CHECK (event_status IN ('SUCCESS', 'FAILURE', 'WARNING'))
);

-- Indexes for Audit Logs
CREATE INDEX idx_audit_logs_organization_id ON audit_logs(organization_id);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_metadata_gin ON audit_logs USING gin(metadata);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at
CREATE TRIGGER update_organizations_updated_at 
    BEFORE UPDATE ON organizations 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_organization_settings_updated_at 
    BEFORE UPDATE ON organization_settings 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert default system organization for bootstrapping
INSERT INTO organizations (id, name, slug, status) 
VALUES ('00000000-0000-0000-0000-000000000001', 'System', 'system', 'ACTIVE');

INSERT INTO organization_settings (organization_id) 
VALUES ('00000000-0000-0000-0000-000000000001');
