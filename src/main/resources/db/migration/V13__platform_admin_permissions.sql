-- MeiCrypt Identity Platform - Phase 12 & 13
-- Version: V13
-- Description:
--   Phase 12 (Module 12.1): Global platform administrator permissions.
--     Introduces the `platform:*` permission family and a system-wide
--     "platform_admin" role scoped to a synthetic PLATFORM organization.
--   Phase 13 (Module 13.1): Developer portal permissions for self-service
--     credential rotation and callback URI management.
--
-- Design contracts:
--   * A single, well-known synthetic organization named "MeiCrypt Platform"
--     hosts the platform_admin role. Its id is stable across environments
--     via a deterministic namespace UUID so scripts can address it.
--   * Developer permissions are org-scoped (they extend RBAC) and are
--     seeded into the platform's OWNER role automatically via join query.

-- =====================================================================
-- Extend the permission catalog with platform-level and developer scopes
-- =====================================================================
INSERT INTO permissions (code, domain, resource, action, description) VALUES
    -- Phase 12: platform administration (super-admin scope)
    ('platform:organization:read',   'platform', 'organization', 'read',
     'View any organization across the platform'),
    ('platform:organization:manage', 'platform', 'organization', 'manage',
     'Create, suspend, or delete any organization'),
    ('platform:user:read',           'platform', 'user',         'read',
     'Read any user across the platform'),
    ('platform:user:manage',         'platform', 'user',         'manage',
     'Suspend, unlock, or wipe any user across the platform'),
    ('platform:session:read',        'platform', 'session',      'read',
     'Read any active session across the platform'),
    ('platform:session:revoke',      'platform', 'session',      'revoke',
     'Revoke any session across the platform'),
    ('platform:audit:read',          'platform', 'audit',        'read',
     'Read the platform-wide audit trail'),
    ('platform:notification:read',   'platform', 'notification', 'read',
     'Inspect the platform-wide notification outbox'),

    -- Phase 13: developer portal (self-service application management)
    ('developer:application:read',        'developer', 'application', 'read',
     'View the developer''s own registered client applications'),
    ('developer:application:manage',      'developer', 'application', 'manage',
     'Register, edit, or delete the developer''s own client applications'),
    ('developer:application:rotate_secret','developer','application','rotate_secret',
     'Rotate the client_secret of the developer''s own client applications');

-- =====================================================================
-- Create the synthetic "MeiCrypt Platform" organization if missing. All
-- platform admins live inside this org. The row is addressed by slug
-- (`meicrypt-platform`) — we let PostgreSQL generate the UUID so we never
-- collide with a pre-existing row (e.g. the bootstrap 'System' org from V1).
-- =====================================================================
INSERT INTO organizations (name, slug, status)
SELECT 'MeiCrypt Platform', 'meicrypt-platform', 'ACTIVE'
 WHERE NOT EXISTS (SELECT 1 FROM organizations WHERE slug = 'meicrypt-platform');

-- Ensure a platform_admin role exists inside the synthetic org.
INSERT INTO roles (organization_id, name, slug, description, role_type, is_default)
SELECT o.id, 'Platform Administrator', 'platform-admin',
       'Full authority over every organization managed by MeiCrypt.',
       'SYSTEM', FALSE
  FROM organizations o
 WHERE o.slug = 'meicrypt-platform'
   AND NOT EXISTS (
        SELECT 1 FROM roles r
         WHERE r.organization_id = o.id AND r.slug = 'platform-admin'
       );

-- platform-admin gets every platform:* permission plus baseline read on rbac / audit.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN organizations o ON o.id = r.organization_id AND o.slug = 'meicrypt-platform'
  JOIN permissions p ON p.domain IN ('platform', 'rbac', 'audit')
 WHERE r.slug = 'platform-admin'
   AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
         WHERE rp.role_id = r.id AND rp.permission_id = p.id
       );

-- Every existing OWNER role gains the new developer:* permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.domain = 'developer'
 WHERE r.slug = 'owner'
   AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
         WHERE rp.role_id = r.id AND rp.permission_id = p.id
       );

-- Existing ADMIN roles inside each organization also receive
-- developer:application:read + manage (but NOT rotate_secret; secret rotation
-- is reserved for the OWNER to avoid a lower-privilege actor stealing keys).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.code IN (
        'developer:application:read',
        'developer:application:manage'
       )
 WHERE r.slug = 'admin'
   AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
         WHERE rp.role_id = r.id AND rp.permission_id = p.id
       );
