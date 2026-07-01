-- MeiCrypt Identity Platform - RBAC System Role Bootstrap
-- Version: V6
-- Description: Provisions the OWNER / ADMIN / MEMBER SYSTEM roles for every
--              existing organization, and attaches the standard permission
--              bundles. Also arranges for the platform default role
--              ("MEMBER") to be applied automatically to new memberships.
--
-- IMPORTANT: This migration seeds data only for organizations that already
-- exist at migration time. New organizations created after this migration
-- runs are handled by the application layer (a listener creates the same
-- baseline roles). The application layer is authoritative going forward.

-- ----------------------------------------------------------------------------
-- Helper: emit a role for every organization, only if it does not already
-- exist. Uses INSERT ... SELECT ... WHERE NOT EXISTS to remain idempotent.
-- ----------------------------------------------------------------------------
INSERT INTO roles (organization_id, name, slug, description, role_type, is_default)
SELECT o.id, 'Owner', 'owner',
       'Full control over the organization, including billing and ownership transfer.',
       'SYSTEM', FALSE
  FROM organizations o
 WHERE NOT EXISTS (
        SELECT 1 FROM roles r
         WHERE r.organization_id = o.id AND r.slug = 'owner'
       );

INSERT INTO roles (organization_id, name, slug, description, role_type, is_default)
SELECT o.id, 'Administrator', 'admin',
       'Manage members, roles, applications and organization settings.',
       'SYSTEM', FALSE
  FROM organizations o
 WHERE NOT EXISTS (
        SELECT 1 FROM roles r
         WHERE r.organization_id = o.id AND r.slug = 'admin'
       );

INSERT INTO roles (organization_id, name, slug, description, role_type, is_default)
SELECT o.id, 'Member', 'member',
       'Standard organization member with baseline read access.',
       'SYSTEM', TRUE
  FROM organizations o
 WHERE NOT EXISTS (
        SELECT 1 FROM roles r
         WHERE r.organization_id = o.id AND r.slug = 'member'
       );

-- ----------------------------------------------------------------------------
-- OWNER gets every permission in the catalog.
-- ----------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.slug = 'owner'
   AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
         WHERE rp.role_id = r.id AND rp.permission_id = p.id
       );

-- ----------------------------------------------------------------------------
-- ADMIN: manage org operations but not delete the organization itself.
-- ----------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.code IN (
        'organization:organization:read',
        'organization:organization:update',
        'organization:membership:read',
        'organization:membership:manage',
        'organization:invitation:read',
        'organization:invitation:manage',
        'organization:domain:read',
        'organization:domain:manage',
        'identity:user:read',
        'identity:user:update',
        'identity:user:suspend',
        'identity:session:read',
        'identity:session:revoke',
        'rbac:role:read',
        'rbac:role:manage',
        'rbac:permission:read',
        'rbac:assignment:read',
        'rbac:assignment:manage',
        'oauth:application:read',
        'oauth:application:manage',
        'audit:log:read'
       )
 WHERE r.slug = 'admin'
   AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
         WHERE rp.role_id = r.id AND rp.permission_id = p.id
       );

-- ----------------------------------------------------------------------------
-- MEMBER: baseline read-only visibility inside the organization.
-- ----------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.code IN (
        'organization:organization:read',
        'organization:membership:read',
        'rbac:role:read',
        'rbac:permission:read'
       )
 WHERE r.slug = 'member'
   AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
         WHERE rp.role_id = r.id AND rp.permission_id = p.id
       );

-- ----------------------------------------------------------------------------
-- Backfill: apply default role assignments to every ACTIVE membership that
-- doesn't yet have any role. Ensures the platform behaves consistently for
-- organizations that predated this migration.
-- ----------------------------------------------------------------------------
INSERT INTO membership_role_assignments (membership_id, role_id)
SELECT m.id, r.id
  FROM organization_memberships m
  JOIN roles r ON r.organization_id = m.organization_id
              AND r.is_default = TRUE
 WHERE m.status = 'ACTIVE'
   AND NOT EXISTS (
        SELECT 1 FROM membership_role_assignments mra
         WHERE mra.membership_id = m.id
       );
