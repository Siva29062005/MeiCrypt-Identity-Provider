# Phase 4 Verification Guide — RBAC Authorization Framework

This guide verifies that **Phase 4 (RBAC Authorization Framework)** of the MeiCrypt
Identity Platform is correctly implemented and operational.

## ✅ Phase 4 Modules Implemented

| Module | Description                                                    | Status |
|--------|----------------------------------------------------------------|--------|
| 4.1    | Permission Model (`domain:resource:action` catalog)            | ✓      |
| 4.2    | Role Configurations (org-scoped SYSTEM + CUSTOM roles)         | ✓      |
| 4.3    | Assignment Engine (membership ↔ role linking)                  | ✓      |
| 4.4    | Evaluation Directives (`@PreAuthorize` + `@rbac.*` SpEL helper)| ✓      |

New files under `com.meicrypt.identity.rbac.*`:

```
rbac/
├── controller/{PermissionController,RoleController,RoleAssignmentController}.java
├── dto/{PermissionDTO,RoleDTO,CreateRoleRequest,UpdateRoleRequest,
│        RoleAssignmentDTO,AssignRoleRequest}.java
├── entity/{Permission,Role,RoleType,MembershipRoleAssignment}.java
├── exception/{RoleNotFoundException,PermissionNotFoundException,
│              ImmutableRoleException,CrossTenantRoleException}.java
├── mapper/RbacMapper.java
├── repository/{PermissionRepository,RoleRepository,
│               MembershipRoleAssignmentRepository}.java
├── security/RbacSecurity.java
└── service/{PermissionService,RoleService,RoleAssignmentService,
             SystemRoleBootstrapper,UserAuthorityService}.java
```

Additional changes:
- `db/migration/V5__rbac_permissions_roles.sql` → 4 new tables (`permissions`,
  `roles`, `role_permissions`, `membership_role_assignments`) + 23 seeded permissions.
- `db/migration/V6__rbac_bootstrap_system_roles.sql` → provisions OWNER / ADMIN /
  MEMBER SYSTEM roles for every existing organization and backfills default
  assignments for existing ACTIVE memberships.
- `config/SecurityConfiguration.java` → `@EnableMethodSecurity(prePostEnabled=true)`.
- `auth/security/JwtAuthenticationFilter.java` → loads real authorities via
  `UserAuthorityService` (permission codes + `ROLE_<slug>`).
- `organization/service/OrganizationService.java` → calls
  `SystemRoleBootstrapper.bootstrap(orgId)` when a new org is created.
- `common/exception/GlobalExceptionHandler.java` → RFC 7807 handlers for the
  4 new RBAC exceptions.

---

## 📋 Pre-Verification Checklist

### 1. Infrastructure Up

```bash
docker-compose ps
```
Expected: `meicrypt-postgres`, `meicrypt-redis` are `Up (healthy)`.

### 2. Database Schema

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "\dt" | \
  grep -E 'permissions|^ public \| roles|role_permissions|membership_role_assignments'
```
Expected new tables:
- `permissions`
- `roles`
- `role_permissions`
- `membership_role_assignments`

### 3. Flyway Migration History

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```
Expected the two trailing rows:

```
 5 | rbac permissions roles          | t
 6 | rbac bootstrap system roles     | t
```

### 4. Permission Catalog Seeded

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT COUNT(*) AS total_permissions FROM permissions;"

docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT domain, COUNT(*) FROM permissions GROUP BY domain ORDER BY domain;"
```
Expected: `total_permissions = 23`, split across the `organization`, `identity`,
`rbac`, `oauth`, `audit` domains.

### 5. SYSTEM Roles Provisioned

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT o.slug AS org, r.slug AS role, r.role_type, r.is_default
     FROM roles r JOIN organizations o ON o.id = r.organization_id
    ORDER BY o.slug, r.slug;"
```
Expected: every organization has 3 rows (`admin`, `member`, `owner`) all of
type `SYSTEM`; `member` is marked `is_default = t`.

### 6. Compile

```bash
mvn -q clean compile
```
Expected: exit code 0, no errors.

### 7. Start the App

```bash
mvn spring-boot:run
```
Expected: application boots on port `8080`. Flyway applies V5 and V6 on first run.

---

## 🧪 API Verification

> ℹ️  **Tip**: everything below is wrapped up in the **`verify-phase4-api.sh`**
> helper (see §Quick verify). The commands here are the same ones the script
> runs, laid out so you can paste them one by one and see the actual JSON /
> HTTP responses in your terminal.

### 0. Prerequisites

1. App is running (`mvn spring-boot:run`) on port 8080.
2. Postgres container is `meicrypt-postgres` (docker-compose default).
3. `curl`, `jq`, and `docker` are on your PATH.

Set common variables:

```bash
BASE=http://localhost:8080
ORG=00000000-0000-0000-0000-000000000001
PG="docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity"
```

Register alice + bob if they don't already exist (safe to re-run — a
duplicate email just returns 409):

```bash
for EMAIL in alice@example.com bob@example.com; do
  curl -s -o /dev/null -w "$EMAIL -> HTTP %{http_code}\n" \
    -X POST "$BASE/api/v1/users/register" -H 'Content-Type: application/json' \
    -d "{
       \"organizationId\":\"$ORG\",
       \"email\":\"$EMAIL\",
       \"password\":\"SecurePass123!\",
       \"firstName\":\"${EMAIL%@*}\",
       \"lastName\":\"Test\"
     }"
done
```

Verify both emails using the pending tokens in the DB:

```bash
for EMAIL in alice@example.com bob@example.com; do
  TOK=$($PG -tA -c "SELECT token FROM verification_tokens
                     WHERE user_id=(SELECT id FROM users WHERE email='$EMAIL')
                       AND status='PENDING'
                     ORDER BY created_at DESC LIMIT 1;")
  [ -n "$TOK" ] && curl -s -o /dev/null -X POST "$BASE/api/v1/verification/verify-email" \
       -H 'Content-Type: application/json' -d "{\"token\":\"$TOK\"}"
  echo "verified $EMAIL"
done
```

Bootstrap memberships (Phase 2 registration doesn't create these automatically
yet) and grant Alice=admin, Bob=member:

```bash
for E in alice@example.com bob@example.com; do
  UID=$($PG -tA -c "SELECT id FROM users WHERE email='$E';")
  $PG -q -c "INSERT INTO organization_memberships
             (organization_id, user_id, role, status)
             SELECT '$ORG','$UID','MEMBER','ACTIVE'
             WHERE NOT EXISTS (SELECT 1 FROM organization_memberships
                                WHERE user_id='$UID' AND organization_id='$ORG');"
done

MID_ALICE=$($PG -tA -c "SELECT m.id FROM organization_memberships m
                          JOIN users u ON u.id=m.user_id
                         WHERE u.email='alice@example.com' AND m.organization_id='$ORG';")
MID_BOB=$($PG -tA -c   "SELECT m.id FROM organization_memberships m
                          JOIN users u ON u.id=m.user_id
                         WHERE u.email='bob@example.com'   AND m.organization_id='$ORG';")
ADMIN_ROLE=$($PG -tA -c "SELECT id FROM roles WHERE organization_id='$ORG' AND slug='admin';")
MEMBER_ROLE=$($PG -tA -c "SELECT id FROM roles WHERE organization_id='$ORG' AND slug='member';")

$PG -q -c "INSERT INTO membership_role_assignments (membership_id, role_id)
           VALUES ('$MID_ALICE','$ADMIN_ROLE') ON CONFLICT DO NOTHING;"
$PG -q -c "INSERT INTO membership_role_assignments (membership_id, role_id)
           VALUES ('$MID_BOB','$MEMBER_ROLE') ON CONFLICT DO NOTHING;"

echo "MID_ALICE=$MID_ALICE  MID_BOB=$MID_BOB"
```

Now log in and cache each token:

```bash
login() {
  curl -s -X POST "$BASE/api/v1/auth/login" \
       -H 'Content-Type: application/json' \
       -d "{\"organizationId\":\"$ORG\",\"email\":\"$1\",\"password\":\"SecurePass123!\"}" \
    | jq -r '.accessToken'
}
ACCESS=$(login alice@example.com)   # alice = admin
BOB=$(login   bob@example.com)      # bob   = member
echo "Alice ${ACCESS:0:40}...   Bob ${BOB:0:40}..."
```

---

### Module 4.1 — List permission catalog

```bash
curl -s "$BASE/api/v1/rbac/permissions" \
     -H "Authorization: Bearer $ACCESS" | jq 'length, .[0]'
```
Expected:
- length = `23`
- Each element shape (note: field is `system`, not `isSystem`):
  ```json
  {
    "id": "…uuid…",
    "code": "organization:organization:read",
    "domain": "organization",
    "resource": "organization",
    "action": "read",
    "description": "View organization details",
    "system": true
  }
  ```

Filter by domain:
```bash
curl -s "$BASE/api/v1/rbac/permissions?domain=rbac" \
     -H "Authorization: Bearer $ACCESS" | jq -r '.[] | .code'
```
Expected: `rbac:role:read`, `rbac:role:manage`, `rbac:permission:read`,
`rbac:assignment:read`, `rbac:assignment:manage`.

Anonymous request must be denied:
```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' "$BASE/api/v1/rbac/permissions"
```
Expected: **HTTP 403** (Spring Security's default translation for a missing
JWT on a `.authenticated()` route).

---

### Module 4.2 — Role CRUD (organization-scoped)

List roles (should show 3 SYSTEM roles right after bootstrap):
```bash
curl -s "$BASE/api/v1/organizations/$ORG/roles" \
     -H "Authorization: Bearer $ACCESS" | jq '.[] | {slug, roleType, defaultRole}'
```
Expected slugs: `owner`, `admin`, `member`; all `"roleType":"SYSTEM"`;
`member.defaultRole == true`.

Create a CUSTOM role:
```bash
NEW=$(curl -s -X POST "$BASE/api/v1/organizations/$ORG/roles" \
  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
  -d '{
    "name":"Session Auditor",
    "description":"Read active sessions and audit logs",
    "permissionCodes":["identity:session:read","audit:log:read"]
  }')
echo "$NEW" | jq
RID=$(echo "$NEW" | jq -r '.id')
```
Expected: response contains `"roleType":"CUSTOM"` and `permissionCodes` mirrors
what was sent.

Update the role:
```bash
curl -s -X PATCH "$BASE/api/v1/organizations/$ORG/roles/$RID" \
  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
  -d '{
    "description":"Read sessions, devices and audit logs",
    "permissionCodes":["identity:session:read","identity:session:revoke","audit:log:read"]
  }' | jq
```

Attempt to delete a SYSTEM role (must be blocked):
```bash
SYS=$(curl -s "$BASE/api/v1/organizations/$ORG/roles" \
        -H "Authorization: Bearer $ACCESS" | jq -r '.[] | select(.slug=="admin") | .id')

curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
     -X DELETE "$BASE/api/v1/organizations/$ORG/roles/$SYS" \
     -H "Authorization: Bearer $ACCESS"
```
Expected: **HTTP 409** (`immutable-role`).

Attempt to create with an unknown permission code:
```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
     -X POST "$BASE/api/v1/organizations/$ORG/roles" \
     -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
     -d '{"name":"Broken","permissionCodes":["bogus:permission:code"]}'
```
Expected: **HTTP 400** (`permission-not-found`).

---

### Module 4.3 — Assign / revoke roles

List Alice's current assignments (note: field is `roleSlug`, not `slug`):
```bash
curl -s "$BASE/api/v1/organizations/$ORG/memberships/$MID_ALICE/role-assignments" \
     -H "Authorization: Bearer $ACCESS" | jq '.[] | {roleSlug, roleName, roleId}'
```
Expected: contains at least `{"roleSlug":"admin", ...}`.

Assign the new CUSTOM role to Bob:
```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
     -X POST "$BASE/api/v1/organizations/$ORG/memberships/$MID_BOB/role-assignments" \
     -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
     -d "{\"roleId\":\"$RID\"}"
```
Expected: **HTTP 201**.

Duplicate assignment (must fail cleanly):
```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
     -X POST "$BASE/api/v1/organizations/$ORG/memberships/$MID_BOB/role-assignments" \
     -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
     -d "{\"roleId\":\"$RID\"}"
```
Expected: **HTTP 409**.

Cross-tenant assignment (only runnable when a second org exists):
```bash
OTHER_ROLE=$($PG -tA -c "SELECT r.id FROM roles r
                          JOIN organizations o ON o.id=r.organization_id
                         WHERE o.id <> '$ORG' LIMIT 1;")
if [ -n "$OTHER_ROLE" ]; then
  curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
     -X POST "$BASE/api/v1/organizations/$ORG/memberships/$MID_ALICE/role-assignments" \
     -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
     -d "{\"roleId\":\"$OTHER_ROLE\"}"
fi
```
Expected (if run): **HTTP 403** (`cross-tenant-role`).

Revoke Bob's custom role:
```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
     -X DELETE "$BASE/api/v1/organizations/$ORG/memberships/$MID_BOB/role-assignments/$RID" \
     -H "Authorization: Bearer $ACCESS"
```
Expected: **HTTP 204**.

---

### Module 4.4 — Evaluation directives (`@PreAuthorize`)

Bob (member only) cannot create roles:
```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
     -X POST "$BASE/api/v1/organizations/$ORG/roles" \
     -H "Authorization: Bearer $BOB" -H 'Content-Type: application/json' \
     -d '{"name":"Nope","permissionCodes":["identity:user:read"]}'
```
Expected: **HTTP 403**.

Bob CAN list roles (member has `rbac:role:read`):
```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
     "$BASE/api/v1/organizations/$ORG/roles" \
     -H "Authorization: Bearer $BOB"
```
Expected: **HTTP 200**.

Cross-tenant access — Bob hits an organization he doesn't belong to:
```bash
OTHER_ORG=$($PG -tA -c "SELECT id FROM organizations WHERE id <> '$ORG' LIMIT 1;")
if [ -n "$OTHER_ORG" ]; then
  curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
       "$BASE/api/v1/organizations/$OTHER_ORG/roles" \
       -H "Authorization: Bearer $BOB"
fi
```
Expected (if run): **HTTP 403** — enforced by
`@rbac.sameOrganization(#organizationId)`.

Inspect Alice's effective permission set straight from the DB
(these are exactly the codes that get injected as `GrantedAuthority` values
by `UserAuthorityService` on every request):
```bash
$PG -c "SELECT p.code
         FROM users u
         JOIN organization_memberships m ON m.user_id = u.id
         JOIN membership_role_assignments mra ON mra.membership_id = m.id
         JOIN role_permissions rp ON rp.role_id = mra.role_id
         JOIN permissions p ON p.id = rp.permission_id
        WHERE u.email='alice@example.com' AND m.organization_id='$ORG'
        ORDER BY p.code;"
```
Expected: ~21 rows covering `organization:*`, `identity:*`, `rbac:*`,
`oauth:*`, and `audit:log:read`.

---

## 📊 Swagger UI

Open http://localhost:8080/swagger-ui.html — new tag groups:

- **Permissions** — 1 endpoint (`GET /rbac/permissions`)
- **Roles** — 5 endpoints under `/organizations/{orgId}/roles`
- **Role Assignments** — 3 endpoints under `/memberships/{mid}/role-assignments`

---

## ✅ Success Criteria

Phase 4 is complete when **all** of the following hold:

- [x] Migrations `V5` and `V6` applied cleanly.
- [x] `SELECT COUNT(*) FROM permissions` returns `23`.
- [x] Every organization has 3 SYSTEM roles (`owner`, `admin`, `member`);
      `member.is_default = true`.
- [x] `mvn -q clean compile` succeeds.
- [x] `GET /api/v1/rbac/permissions` requires an authenticated user and lists
      the seeded catalog.
- [x] Users without `rbac:role:manage` receive `403` when attempting
      `POST /organizations/{orgId}/roles`.
- [x] Users authenticated in a different organization receive `403` when
      hitting `/organizations/{otherOrgId}/roles` (multi-tenant enforcement).
- [x] Attempting to modify or delete a SYSTEM role returns `409`
      (`immutable-role`).
- [x] Attempting to assign a role that belongs to a different organization
      returns `403` (`cross-tenant-role`).
- [x] Assigning a CUSTOM role to a membership and then re-logging in exposes
      the role's permission codes as `GrantedAuthority` values on the new JWT.
- [x] Creating a new organization automatically provisions the OWNER / ADMIN /
      MEMBER SYSTEM roles (verify by inspecting `roles` after
      `POST /api/v1/organizations`).

---

## 🚀 Quick verify (one command)

Two helper scripts are shipped with the repo:

```bash
# Schema / migrations / seed / compile — fast, no HTTP calls
./verify-phase4.sh

# Full end-to-end walk-through of every RBAC endpoint (Modules 4.1-4.4)
./verify-phase4-api.sh
```

`verify-phase4-api.sh` provisions two users (alice & bob), grants them the
SYSTEM `admin` / `member` roles, and drives the same commands documented in
this file — printing a coloured PASS/FAIL line per assertion and a final tally
(`✅ Phase 4 API verification passed — all checks succeeded.`).

---

## 🐛 Troubleshooting

**Newly assigned role isn't taking effect**
Authorities are attached to the SecurityContext each request based on the
current JWT's `userId` + `organizationId`. Any change made through
`/role-assignments` is picked up on the **next request** — you do **not** need
to re-login. If a request still shows the old set, confirm the assignment
actually persisted:
```sql
SELECT r.slug FROM membership_role_assignments mra
JOIN roles r ON r.id = mra.role_id
WHERE mra.membership_id = '<membership_uuid>';
```

**409 when creating a role**
The `(organization_id, slug)` pair must be unique. The slug is derived from
`name` if you don't send one — pick a different name or provide an explicit
slug.

**403 on a legitimate call**
Make sure your access token was issued **after** the role assignment. Otherwise
inspect the resolved authorities via
`GET /api/v1/auth/me` (session id) and the DB query in Module 4.4 above.

**`permission-not-found` on role create/update**
The `permissionCodes` array must reference existing rows in `permissions`.
Run `GET /api/v1/rbac/permissions` to see the exact spellings.

---

**Phase 4 complete. Ready for Phase 5 (Client Application Registry). 🎉**
