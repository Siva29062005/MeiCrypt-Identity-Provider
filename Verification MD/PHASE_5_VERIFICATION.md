# Phase 5 Verification Guide — Client Application Registry

Phase 5 introduces the **Client Application Registry** — organizations can
now register their own OAuth2 clients (CRM, ERP, dashboards, service accounts)
and receive **cryptographic credentials** (`client_id` + `client_secret`)
that will later be consumed by the OAuth2/OIDC engine (Phases 6/7).

## ✅ Modules Delivered

| Module | Description                                                       | Status |
|--------|-------------------------------------------------------------------|--------|
| 5.1    | Application Discovery — org-scoped CRUD for client applications   | ✓      |
| 5.2    | Credential Issuance — opaque `client_id`, BCrypt-hashed secret,   | ✓      |
|        |   one-shot plaintext disclosure, rotation endpoint                |        |

### New files under `com.meicrypt.identity.application.*`

```
application/
├── controller/ClientApplicationController.java
├── dto/
│   ├── ClientApplicationDTO.java
│   ├── ClientApplicationCredentialsDTO.java
│   ├── CreateClientApplicationRequest.java
│   ├── CreateClientApplicationResponse.java
│   ├── UpdateClientApplicationRequest.java
│   └── UpdateApplicationStatusRequest.java
├── entity/
│   ├── ApplicationStatus.java
│   ├── ApplicationType.java
│   ├── ClientApplication.java
│   ├── ClientApplicationLogoutUri.java
│   └── ClientApplicationRedirectUri.java
├── exception/
│   ├── ApplicationStateException.java
│   ├── ClientApplicationNotFoundException.java
│   └── PublicClientSecretException.java
├── mapper/ClientApplicationMapper.java
├── repository/
│   ├── ClientApplicationLogoutUriRepository.java
│   ├── ClientApplicationRedirectUriRepository.java
│   └── ClientApplicationRepository.java
└── service/
    ├── ClientApplicationService.java
    └── ClientCredentialGenerator.java
```

Additional changes:

- `db/migration/V7__client_applications.sql` → 3 new tables:
  `client_applications`, `client_application_redirect_uris`,
  `client_application_logout_uris`.
- `common/exception/GlobalExceptionHandler.java` → RFC 7807 handlers for the
  3 new Phase 5 exceptions.

---

## 🔐 Security & Credential Contract

- **`client_id`** — 32 bytes of `SecureRandom` entropy, URL-safe Base64,
  prefixed with `mip_`. Public, safe to log.
- **`client_secret`** — 48 bytes of `SecureRandom` entropy, URL-safe Base64,
  prefixed with `mips_`. Stored **only** as a BCrypt-12 hash.
  Plaintext is returned **exactly once** in the create/rotate response.
- **Public clients** (`SPA`, `MOBILE`) — no secret is issued; PKCE is forced on.
- **Confidential clients** (`WEB`, `SERVICE`) — receive a secret; rotation
  invalidates the previous value atomically.
- **Multi-tenant isolation** — every REST route is guarded by
  `@rbac.sameOrganization(#organizationId)` and the caller must additionally
  hold one of the Phase 4 permissions:
    - `oauth:application:read`   — list / fetch
    - `oauth:application:manage` — create / update / status / delete / rotate

---

## 📋 Pre-Verification Checklist

### 1. Infrastructure & migration

```bash
docker-compose ps          # meicrypt-postgres + meicrypt-redis Up (healthy)
./verify-phase5.sh         # schema / migrations / compile smoke check
```

Expected: `✅ Phase 5 schema verification passed.`

### 2. Flyway migration history

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```
Expected trailing row:
```
 7 | client applications  | t
```

### 3. Tables exist

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "\dt" | \
  grep -E 'client_applications|client_application_(redirect|logout)_uris'
```

### 4. Compile

```bash
mvn -q -DskipTests clean compile
```

---

## 🧪 API Walkthrough

Set common variables (same shape as Phase 4):

```bash
BASE=http://localhost:8080
ORG=00000000-0000-0000-0000-000000000001
PG="docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity"
```

Login as an org admin (Alice from Phase 4). This login MUST happen **after**
Alice has been granted the `admin` SYSTEM role (which includes
`oauth:application:manage` + `oauth:application:read`):

```bash
ACCESS=$(curl -s -X POST "$BASE/api/v1/auth/login" \
              -H 'Content-Type: application/json' \
              -d "{\"organizationId\":\"$ORG\",
                   \"email\":\"alice@example.com\",
                   \"password\":\"SecurePass123!\"}" | jq -r '.accessToken')
echo "Alice token: ${ACCESS:0:40}..."
```

### Module 5.1 — Register a confidential WEB client

```bash
CREATED=$(curl -s -X POST "$BASE/api/v1/organizations/$ORG/applications" \
  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
  -d '{
    "name": "MeiCrypt CRM",
    "description": "Internal CRM used by the sales team",
    "applicationType": "WEB",
    "homepageUrl": "https://crm.meicrypt.local",
    "redirectUris": [
      "https://crm.meicrypt.local/oauth/callback",
      "http://localhost:3000/oauth/callback"
    ],
    "postLogoutRedirectUris": [
      "https://crm.meicrypt.local/"
    ],
    "grantTypes": ["authorization_code","refresh_token"],
    "scopes": ["openid","profile","email","crm.read","crm.write"]
  }')
echo "$CREATED" | jq
APP_ID=$(echo "$CREATED" | jq -r '.application.id')
CLIENT_ID=$(echo "$CREATED" | jq -r '.application.clientId')
CLIENT_SECRET=$(echo "$CREATED" | jq -r '.credentials.clientSecret')
```

Expected:
- **HTTP 201**
- `application.clientId` starts with `mip_`
- `application.hasClientSecret == true`
- `credentials.clientSecret` starts with `mips_` (this is the ONLY time it is
  disclosed — save it now)
- `application.confidential == true` and `requirePkce == true`

### Module 5.1 — List / fetch

```bash
curl -s "$BASE/api/v1/organizations/$ORG/applications" \
     -H "Authorization: Bearer $ACCESS" | jq 'length, .[0].name'

curl -s "$BASE/api/v1/organizations/$ORG/applications/$APP_ID" \
     -H "Authorization: Bearer $ACCESS" | jq '{name, clientId, redirectUris, status}'
```

### Module 5.2 — Rotate the secret

```bash
ROT=$(curl -s -X POST "$BASE/api/v1/organizations/$ORG/applications/$APP_ID/rotate-secret" \
           -H "Authorization: Bearer $ACCESS")
echo "$ROT" | jq
NEW_SECRET=$(echo "$ROT" | jq -r '.clientSecret')
[ "$NEW_SECRET" != "$CLIENT_SECRET" ] && echo "✅ secret rotated"
```

Verify the old secret can no longer authenticate the client (internal
`authenticateClient` API — used by Phase 6):

```bash
$PG -c "SELECT client_secret_last_rotated_at FROM client_applications WHERE id='$APP_ID';"
```

### Module 5.2 — Public client (no secret)

```bash
SPA=$(curl -s -X POST "$BASE/api/v1/organizations/$ORG/applications" \
  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
  -d '{
    "name": "MeiCrypt Web SPA",
    "applicationType": "SPA",
    "redirectUris": ["http://localhost:5173/callback"],
    "scopes": ["openid","profile","email"]
  }')
echo "$SPA" | jq '.application | {clientId, confidential, requirePkce, hasClientSecret}'
echo "$SPA" | jq '.credentials'
```

Expected:
- `application.confidential == false`
- `application.requirePkce == true` (forced on)
- `application.hasClientSecret == false`
- `credentials.clientSecret == null`

Attempting to rotate the secret must fail with HTTP 409:

```bash
SPA_ID=$(echo "$SPA" | jq -r '.application.id')
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
     -X POST "$BASE/api/v1/organizations/$ORG/applications/$SPA_ID/rotate-secret" \
     -H "Authorization: Bearer $ACCESS"
```
Expected: **HTTP 409** (`public-client-secret`).

### Module 5.1 — Update / status transitions

```bash
curl -s -X PATCH "$BASE/api/v1/organizations/$ORG/applications/$APP_ID" \
     -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
     -d '{"description":"CRM (updated)","scopes":["openid","profile","email","crm.read"]}' | jq

curl -s -X PATCH "$BASE/api/v1/organizations/$ORG/applications/$APP_ID/status" \
     -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
     -d '{"status":"SUSPENDED"}' | jq '{name, status}'

curl -s -X PATCH "$BASE/api/v1/organizations/$ORG/applications/$APP_ID/status" \
     -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
     -d '{"status":"ACTIVE"}' | jq '{name, status}'
```

Once an application is `REVOKED`, further transitions must return **HTTP 409**:

```bash
curl -s -X PATCH "$BASE/api/v1/organizations/$ORG/applications/$APP_ID/status" \
     -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
     -d '{"status":"REVOKED"}' | jq '.status'

curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
     -X PATCH "$BASE/api/v1/organizations/$ORG/applications/$APP_ID/status" \
     -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
     -d '{"status":"ACTIVE"}'
```
Expected: **HTTP 409** (`application-state`).

### RBAC — member (read-only) & cross-tenant

```bash
BOB=$(curl -s -X POST "$BASE/api/v1/auth/login" \
           -H 'Content-Type: application/json' \
           -d "{\"organizationId\":\"$ORG\",
                \"email\":\"bob@example.com\",
                \"password\":\"SecurePass123!\"}" | jq -r '.accessToken')

# Bob only has 'member' - no 'oauth:application:*' permissions
curl -s -o /dev/null -w 'list HTTP %{http_code}\n' \
     "$BASE/api/v1/organizations/$ORG/applications" \
     -H "Authorization: Bearer $BOB"

curl -s -o /dev/null -w 'create HTTP %{http_code}\n' \
     -X POST "$BASE/api/v1/organizations/$ORG/applications" \
     -H "Authorization: Bearer $BOB" -H 'Content-Type: application/json' \
     -d '{"name":"Nope","applicationType":"WEB"}'
```
Expected:
- list → **HTTP 403**
- create → **HTTP 403**

Cross-tenant (Alice trying to hit another organization's registry) also
returns **HTTP 403** thanks to `@rbac.sameOrganization`.

### Delete

```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
     -X DELETE "$BASE/api/v1/organizations/$ORG/applications/$SPA_ID" \
     -H "Authorization: Bearer $ACCESS"
```
Expected: **HTTP 204**. Redirect URIs / logout URIs cascade-delete via
`ON DELETE CASCADE`.

---

## 📊 Swagger UI

Open http://localhost:8080/swagger-ui.html — new tag group:

- **Client Applications** — 6 endpoints under
  `/organizations/{orgId}/applications`

---

## ✅ Success Criteria

Phase 5 is complete when **all** of the following hold:

- [x] Migration `V7` applied cleanly.
- [x] `client_applications`, `client_application_redirect_uris` and
      `client_application_logout_uris` tables exist.
- [x] `mvn -q clean compile` succeeds.
- [x] `POST /api/v1/organizations/{orgId}/applications` (confidential type)
      returns HTTP 201 with a one-shot plaintext `client_secret`, and the
      persisted row stores only a BCrypt hash.
- [x] Public clients (`SPA`, `MOBILE`) never receive a `client_secret` and
      always have `requirePkce = true`.
- [x] Rotating the secret produces a new `mips_…` value and updates
      `client_secret_last_rotated_at`.
- [x] Rotating a secret on a public client returns **409 (`public-client-secret`)**.
- [x] Transitioning a `REVOKED` application returns **409 (`application-state`)**.
- [x] Callers without `oauth:application:manage` receive **403** for
      write endpoints.
- [x] Callers hitting a different organization's registry receive **403**
      (multi-tenant enforcement via `@rbac.sameOrganization`).

---

## 🐛 Troubleshooting

**403 despite correct role**
Access tokens are stateless — but the JWT filter re-hydrates authorities from
`membership_role_assignments` on every request. If a fresh permission still
isn't visible, confirm the assignment actually persisted and matches the
organization in the JWT:
```sql
SELECT p.code
  FROM users u
  JOIN organization_memberships m  ON m.user_id = u.id
  JOIN membership_role_assignments mra ON mra.membership_id = m.id
  JOIN role_permissions rp ON rp.role_id = mra.role_id
  JOIN permissions p ON p.id = rp.permission_id
 WHERE u.email = 'alice@example.com'
   AND m.organization_id = '00000000-0000-0000-0000-000000000001'
   AND p.code LIKE 'oauth:%';
```

**"Cannot derive slug"**
Provide either a non-empty `slug` or a `name` that contains at least one
`[a-z0-9]` character.

**Secret disappeared**
The plaintext secret is displayed exactly once. If it was not captured, use
the rotate endpoint — it will invalidate the old value and return a fresh one.

---

## 🚀 Quick verify (one command)

Two helper scripts ship with the repo:

```bash
# Schema / migrations / compile — fast, no HTTP calls
./verify-phase5.sh

# Full end-to-end walk-through of every registry endpoint (Modules 5.1 + 5.2)
./verify-phase5-api.sh
```

`verify-phase5-api.sh` provisions alice + bob (identical to the Phase 4
harness), grants them the SYSTEM `admin` / `member` roles, registers a
confidential WEB client, rotates its secret, registers a public SPA client,
confirms the SPA cannot hold a secret, walks the ACTIVE → SUSPENDED → ACTIVE
→ REVOKED transition, and finally verifies the RBAC denials — printing a
coloured PASS/FAIL line per assertion and finishing with:

```
✅ Phase 5 API verification passed — all checks succeeded.
```

---

**Phase 5 complete. Ready for Phase 6 (OAuth2 authorization code flow). 🎉**

