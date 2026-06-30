-- MeiCrypt Identity Platform - Organization Membership, Invitations, and Custom Domains
-- Version: V2
-- Description: Phase 1 extensions for organization membership management, invitations, and custom domains

-- Organization Memberships Table (User-Organization Intersection)
CREATE TABLE organization_memberships (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_organization_memberships_organization 
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT organization_memberships_status_check 
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE')),
    CONSTRAINT organization_memberships_role_check 
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'GUEST')),
    CONSTRAINT unique_organization_user_membership 
        UNIQUE (organization_id, user_id)
);

-- Indexes for Organization Memberships
CREATE INDEX idx_organization_memberships_organization_id ON organization_memberships(organization_id);
CREATE INDEX idx_organization_memberships_user_id ON organization_memberships(user_id);
CREATE INDEX idx_organization_memberships_status ON organization_memberships(status);
CREATE INDEX idx_organization_memberships_role ON organization_memberships(role);

-- Organization Invitations Table
CREATE TABLE organization_invitations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    invited_by_user_id UUID,
    invitation_token VARCHAR(500) NOT NULL UNIQUE,
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_organization_invitations_organization 
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT organization_invitations_status_check 
        CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT organization_invitations_role_check 
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'GUEST'))
);

-- Indexes for Organization Invitations
CREATE INDEX idx_organization_invitations_organization_id ON organization_invitations(organization_id);
CREATE INDEX idx_organization_invitations_email ON organization_invitations(email);
CREATE INDEX idx_organization_invitations_token ON organization_invitations(invitation_token);
CREATE INDEX idx_organization_invitations_status ON organization_invitations(status);
CREATE INDEX idx_organization_invitations_expires_at ON organization_invitations(expires_at);

-- Organization Custom Domains Table
CREATE TABLE organization_custom_domains (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL,
    domain VARCHAR(255) NOT NULL UNIQUE,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verification_token VARCHAR(500) NOT NULL UNIQUE,
    verification_method VARCHAR(50) NOT NULL DEFAULT 'DNS_TXT',
    verified_at TIMESTAMP WITH TIME ZONE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_organization_custom_domains_organization 
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT organization_custom_domains_verification_status_check 
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'FAILED')),
    CONSTRAINT organization_custom_domains_verification_method_check 
        CHECK (verification_method IN ('DNS_TXT', 'DNS_CNAME', 'HTTP_FILE', 'EMAIL'))
);

-- Indexes for Organization Custom Domains
CREATE INDEX idx_organization_custom_domains_organization_id ON organization_custom_domains(organization_id);
CREATE INDEX idx_organization_custom_domains_domain ON organization_custom_domains(domain);
CREATE INDEX idx_organization_custom_domains_verification_status ON organization_custom_domains(verification_status);
CREATE INDEX idx_organization_custom_domains_is_primary ON organization_custom_domains(is_primary);

-- Triggers for updated_at
CREATE TRIGGER update_organization_memberships_updated_at 
    BEFORE UPDATE ON organization_memberships 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_organization_invitations_updated_at 
    BEFORE UPDATE ON organization_invitations 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_organization_custom_domains_updated_at 
    BEFORE UPDATE ON organization_custom_domains 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE organization_memberships IS 'Links users to organizations with specific roles and status';
COMMENT ON TABLE organization_invitations IS 'Manages invitation workflow for new organization members';
COMMENT ON TABLE organization_custom_domains IS 'Custom domain management for enterprise identity federation';
