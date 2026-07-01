-- MeiCrypt Identity Platform - RBAC Authorization Framework
-- Version: V5
-- Description: Phase 4 - Permissions, Roles, Role-Permission mappings, Role Assignments

-- ============================================================================
-- Module 4.1 - Permission Model
-- Hardcoded system permissions using the "domain:resource:action" convention.
-- Permissions are global (not org-scoped): they represent the vocabulary of the
-- platform. Only roles are org-scoped.
-- ============================================================================
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(150) NOT NULL UNIQUE,          -- e.g. identity:user:read
    domain VARCHAR(50) NOT NULL,                -- e.g. identity, organization, oauth
    resource VARCHAR(50) NOT NULL,              -- e.g. user, role, application
    action VARCHAR(50) NOT NULL,                -- e.g. read, write, delete, invite
    description VARCHAR(500),
    is_system BOOLEAN NOT NULL DEFAULT TRUE,    -- system-defined, immutable
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT permissions_code_format_check
        CHECK (code ~ '^[a-z0-9_]+:[a-z0-9_]+:[a-z0-9_]+$')
);

CREATE INDEX idx_permissions_domain ON permissions(domain);
CREATE INDEX idx_permissions_resource ON permissions(resource);
CREATE INDEX idx_permissions_action ON permissions(action);

COMMENT ON TABLE permissions IS 'System-defined permission catalog (domain:resource:action)';

-- ============================================================================
-- Module 4.2 - Role Configurations
-- Roles are scoped to an organization. Two kinds:
--   - SYSTEM roles: pre-provisioned per org (e.g. OWNER, ADMIN, MEMBER) - immutable
--   - CUSTOM roles: created by organization admins, combining permissions freely
-- ============================================================================
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    role_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOM',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,    -- assigned automatically on join
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_roles_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT roles_type_check
        CHECK (role_type IN ('SYSTEM', 'CUSTOM')),
    CONSTRAINT unique_role_slug_per_org
        UNIQUE (organization_id, slug)
);

CREATE INDEX idx_roles_organization_id ON roles(organization_id);
CREATE INDEX idx_roles_type ON roles(role_type);
CREATE INDEX idx_roles_slug ON roles(slug);

CREATE TRIGGER update_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE roles IS 'Organization-scoped role definitions (SYSTEM or CUSTOM)';

-- ============================================================================
-- Role <-> Permission mapping (many-to-many)
-- A role bundles a collection of permissions.
-- ============================================================================
CREATE TABLE role_permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by_user_id UUID,

    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    CONSTRAINT unique_role_permission
        UNIQUE (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

COMMENT ON TABLE role_permissions IS 'Maps roles to their allowed permissions';

-- ============================================================================
-- Module 4.3 - Assignment Engine
-- Membership <-> Role association. A membership can hold multiple roles.
-- Roles must belong to the same organization as the membership (enforced in service).
-- ============================================================================
CREATE TABLE membership_role_assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    membership_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by_user_id UUID,

    CONSTRAINT fk_mra_membership
        FOREIGN KEY (membership_id) REFERENCES organization_memberships(id) ON DELETE CASCADE,
    CONSTRAINT fk_mra_role
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT unique_membership_role
        UNIQUE (membership_id, role_id)
);

CREATE INDEX idx_mra_membership_id ON membership_role_assignments(membership_id);
CREATE INDEX idx_mra_role_id ON membership_role_assignments(role_id);

COMMENT ON TABLE membership_role_assignments IS 'Assigns roles to organization memberships';

-- ============================================================================
-- Seed the system permission catalog
-- Convention: <domain>:<resource>:<action>
-- ============================================================================
INSERT INTO permissions (code, domain, resource, action, description) VALUES
    -- Organization domain
    ('organization:organization:read',   'organization', 'organization', 'read',   'View organization details'),
    ('organization:organization:update', 'organization', 'organization', 'update', 'Modify organization details and settings'),
    ('organization:organization:delete', 'organization', 'organization', 'delete', 'Delete or suspend the organization'),
    ('organization:membership:read',     'organization', 'membership',   'read',   'List organization members'),
    ('organization:membership:manage',   'organization', 'membership',   'manage', 'Add, remove, suspend organization members'),
    ('organization:invitation:read',     'organization', 'invitation',   'read',   'View pending invitations'),
    ('organization:invitation:manage',   'organization', 'invitation',   'manage', 'Create and revoke invitations'),
    ('organization:domain:read',         'organization', 'domain',       'read',   'View custom domains'),
    ('organization:domain:manage',       'organization', 'domain',       'manage', 'Register and verify custom domains'),

    -- Identity / User domain
    ('identity:user:read',    'identity', 'user',    'read',    'View user profiles inside the organization'),
    ('identity:user:update',  'identity', 'user',    'update',  'Update user profile attributes'),
    ('identity:user:suspend', 'identity', 'user',    'suspend', 'Suspend user access'),
    ('identity:user:delete',  'identity', 'user',    'delete',  'Fully delete or wipe a user'),
    ('identity:session:read', 'identity', 'session', 'read',    'View active sessions for users'),
    ('identity:session:revoke', 'identity', 'session', 'revoke', 'Force-terminate user sessions'),

    -- RBAC domain (Phase 4 self-management)
    ('rbac:role:read',           'rbac', 'role',       'read',   'View roles inside the organization'),
    ('rbac:role:manage',         'rbac', 'role',       'manage', 'Create, edit, delete custom roles'),
    ('rbac:permission:read',     'rbac', 'permission', 'read',   'View available permissions catalog'),
    ('rbac:assignment:read',     'rbac', 'assignment', 'read',   'View role assignments'),
    ('rbac:assignment:manage',   'rbac', 'assignment', 'manage', 'Assign or revoke roles from memberships'),

    -- OAuth / Application domain (used later in Phase 5+)
    ('oauth:application:read',   'oauth', 'application', 'read',   'View registered OAuth client applications'),
    ('oauth:application:manage', 'oauth', 'application', 'manage', 'Create, edit, revoke OAuth applications'),

    -- Audit
    ('audit:log:read', 'audit', 'log', 'read', 'View audit logs for the organization');
