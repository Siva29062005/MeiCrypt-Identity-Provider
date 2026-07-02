-- MeiCrypt Identity Platform - Membership + Owner-role backfill
-- Version: V14
-- Description:
--   Previous releases of the user-registration flow did not create an
--   `organization_memberships` row (or attach a SYSTEM role) for
--   self-registered users. As a result, existing accounts have no RBAC
--   authorities in their JWT and every organization-scoped endpoint (e.g.
--   POST /roles) returns 403 "Access denied to the requested resource".
--
--   This migration fixes those accounts idempotently:
--     1) Insert an ACTIVE OWNER membership for every user that has no
--        membership in their organization. If the user is the earliest
--        registered user in that org, they are treated as the founding
--        Owner. All other unassigned users get MEMBER (upgraded to
--        Owner further down only if the org still has no owner).
--     2) Ensure every org has at least one OWNER-role assignment.
--     3) Attach the org's default role (typically "member") to every
--        active membership that has no role assignment yet.
--
--   This is safe to re-run; every INSERT is guarded by NOT EXISTS.

-- ---------------------------------------------------------------------------
-- 1) Create memberships for orphaned users.
--    The very first user registered inside an organization is provisioned
--    as OWNER; everyone else becomes MEMBER (RBAC role is attached below).
-- ---------------------------------------------------------------------------
INSERT INTO organization_memberships (organization_id, user_id, role, status, joined_at)
SELECT u.organization_id,
       u.id,
       CASE
           WHEN u.id = (
                SELECT u2.id
                  FROM users u2
                 WHERE u2.organization_id = u.organization_id
                 ORDER BY u2.created_at ASC, u2.id ASC
                 LIMIT 1
           ) THEN 'OWNER'
           ELSE 'MEMBER'
       END,
       'ACTIVE',
       COALESCE(u.created_at, now())
  FROM users u
 WHERE NOT EXISTS (
        SELECT 1
          FROM organization_memberships m
         WHERE m.organization_id = u.organization_id
           AND m.user_id = u.id
       );

-- ---------------------------------------------------------------------------
-- 2) Guarantee every organization has at least one OWNER membership. If the
--    step above already promoted a founding user this is a no-op.
-- ---------------------------------------------------------------------------
UPDATE organization_memberships m
   SET role = 'OWNER'
 WHERE m.id IN (
        SELECT DISTINCT ON (m2.organization_id) m2.id
          FROM organization_memberships m2
         WHERE m2.status = 'ACTIVE'
           AND NOT EXISTS (
                SELECT 1 FROM organization_memberships m3
                 WHERE m3.organization_id = m2.organization_id
                   AND m3.role = 'OWNER'
           )
         ORDER BY m2.organization_id, m2.joined_at ASC, m2.id ASC
 );

-- ---------------------------------------------------------------------------
-- 3) Attach the SYSTEM "owner" role to every membership whose legacy
--    membership.role = 'OWNER' but which is missing a role assignment.
-- ---------------------------------------------------------------------------
INSERT INTO membership_role_assignments (membership_id, role_id)
SELECT m.id, r.id
  FROM organization_memberships m
  JOIN roles r ON r.organization_id = m.organization_id AND r.slug = 'owner'
 WHERE m.role = 'OWNER'
   AND NOT EXISTS (
        SELECT 1 FROM membership_role_assignments mra
         WHERE mra.membership_id = m.id
           AND mra.role_id = r.id
       );

-- ---------------------------------------------------------------------------
-- 4) Attach the org's default role (typically "member") to every ACTIVE
--    membership that still has no role at all. Mirrors the tail of V6.
-- ---------------------------------------------------------------------------
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
