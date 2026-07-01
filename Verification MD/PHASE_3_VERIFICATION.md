# Phase 3 Verification Guide — Authentication & Session Management

This guide verifies that **Phase 3 (Authentication & Session Management)** of the MeiCrypt
Identity Platform is correctly implemented and operational.

## ✅ Phase 3 Modules Implemented

| Module | Description                                            | Status |
|--------|--------------------------------------------------------|--------|
| 3.1    | Credential Verification (login, account state checks)  | ✓      |
| 3.2    | Session Termination (logout / logout-all / by-session) | ✓      |
| 3.3    | Refresh Token Mechanics (rotation + reuse detection)   | ✓      |
| 3.4    | Redis Session State Tracking (live cache)              | ✓      |
| 3.5    | Device Management (UA parsing, per-user devices)       | ✓      |

New files under `com.meicrypt.identity.auth.*`:

```
auth/
├── config/JwtProperties.java
├── controller/AuthenticationController.java
├── controller/SessionController.java
├── controller/DeviceController.java
├── dto/{LoginRequest,LogoutRequest,RefreshTokenRequest,TokenResponse,SessionDTO,DeviceDTO}.java
├── entity/{RefreshToken,RefreshTokenStatus,UserSession,SessionStatus,UserDevice}.java
├── exception/{AuthenticationFailedException,InvalidRefreshTokenException,RefreshTokenReuseException}.java
├── mapper/SessionMapper.java
├── repository/{RefreshTokenRepository,UserSessionRepository,UserDeviceRepository}.java
├── security/{AuthenticatedUser,JwtAuthenticationFilter}.java
└── service/{JwtService,SessionCacheService,DeviceService,AuthenticationService,SessionService}.java
```

Additional changes:
- `pom.xml` → JJWT 0.12.5 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) added.
- `application.yml` → `meicrypt.auth.*` block added.
- `SecurityConfiguration.java` → stateless JWT filter chain.
- `GlobalExceptionHandler.java` → RFC 7807 mapping for the 3 new auth exceptions.
- `db/migration/V4__auth_sessions_and_devices.sql` → 3 new tables.

---

## 📋 Pre-Verification Checklist

### 1. Infrastructure Up

```bash
docker-compose ps
```
Expected: `meicrypt-postgres`, `meicrypt-redis` are `Up (healthy)`.

### 2. Database Schema

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "\dt"
```
Expected new tables in Phase 3:
- `refresh_tokens`
- `user_sessions`
- `user_devices`

### 3. Flyway Migration History

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```
Expected the row `V4 | auth sessions and devices | true` at the tail.

### 4. Compile

```bash
mvn -q clean compile
```
Expected: exit code 0, no errors.

### 5. Start the App

```bash
mvn spring-boot:run
```
Expected: application boots on port `8080`. If Redis is not up, the app still starts —
the session cache degrades to fail-open (see `SessionCacheService`).

---

## 🧪 API Verification

### Prerequisites

You need a **verified, ACTIVE** user. Register + verify one using Phase 2 endpoints:

```bash
# 1) Register (using default seed organization id)
curl -s -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId":"00000000-0000-0000-0000-000000000001",
    "email":"alice@example.com",
    "password":"SecurePass123!",
    "firstName":"Alice",
    "lastName":"Doe"
  }'

# 2) Fetch verification token from DB and verify
TOKEN=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -t -A -c \
  "SELECT token FROM verification_tokens WHERE token_type='EMAIL_VERIFICATION' AND status='PENDING' ORDER BY created_at DESC LIMIT 1;")

curl -s -X POST http://localhost:8080/api/v1/verification/verify-email \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"$TOKEN\"}"
```

---

### Module 3.1 — Login

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId":"00000000-0000-0000-0000-000000000001",
    "email":"alice@example.com",
    "password":"SecurePass123!",
    "deviceName":"Alice-Laptop"
  }' | jq
```

Expected `200 OK` shape:
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "…opaque base64url…",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "sessionId": "…uuid…",
  "userId": "…uuid…"
}
```

Failure modes to sanity-check:
| Attempt                          | Expected                     |
|----------------------------------|------------------------------|
| Wrong password                   | `401 authentication-failed`  |
| Unverified email                 | `401 authentication-failed`  |
| Suspended / inactive account     | `401 authentication-failed`  |
| 5+ wrong passwords in a row      | `403 user-locked`            |

---

### Access-Token Usage

```bash
ACCESS="<paste accessToken>"
curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $ACCESS" | jq
```
Expected: JSON with `userId`, `organizationId`, `sessionId`, `email`, `jti`.

Try without a token:
```bash
curl -i http://localhost:8080/api/v1/auth/me
```
Expected: `401 Unauthorized` (SecurityConfiguration enforces auth on all non-public routes).

---

### Module 3.3 — Refresh Rotation

```bash
REFRESH="<paste refreshToken>"
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}" | jq
```
Expected: brand-new `accessToken` + `refreshToken`. The old refresh token is
now `ROTATED` in `refresh_tokens`.

**Reuse detection** — send the *old* refresh a second time:
```bash
curl -i -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"
```
Expected: `401 refresh-token-reuse`. All refresh tokens for the session
transition to `COMPROMISED` and the `user_sessions` row becomes `TERMINATED`.

Verify in DB:
```sql
SELECT status, revoked_reason FROM refresh_tokens ORDER BY issued_at DESC LIMIT 5;
SELECT status, termination_reason FROM user_sessions ORDER BY created_at DESC LIMIT 5;
```

---

### Module 3.2 — Logout

Single session (using the current refresh token):
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"
```
Expected: `{"message":"Logged out"}`. The corresponding `user_sessions` row moves to
`TERMINATED` and the Redis key `session:{sessionId}` disappears.

Logout **all** sessions for the authenticated user:
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/logout-all \
  -H "Authorization: Bearer $ACCESS"
```

Terminate one specific session by id (admin/utility):
```bash
curl -s -X DELETE http://localhost:8080/api/v1/sessions/{sessionId} \
  -H "Authorization: Bearer $ACCESS"
```

---

### Module 3.4 — Redis Live Session Tracking

Prove the JWT filter honors Redis state:

```bash
# Login → note sessionId + accessToken
# Delete the Redis key manually
docker exec meicrypt-redis redis-cli DEL session:<sessionId>

# Try /auth/me again — should be 401 because the filter sees the session is gone
curl -i http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $ACCESS"
```
Expected: `401 Unauthorized` even though the JWT itself is not yet expired.

Inspect what's cached:
```bash
docker exec meicrypt-redis redis-cli KEYS 'session:*'
docker exec meicrypt-redis redis-cli TTL  session:<sessionId>
```

---

### Module 3.5 — Devices & Sessions Inventory

```bash
curl -s http://localhost:8080/api/v1/devices  -H "Authorization: Bearer $ACCESS" | jq
curl -s http://localhost:8080/api/v1/sessions -H "Authorization: Bearer $ACCESS" | jq
```
Login from a different User-Agent (e.g. `curl -A 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)'`)
to see a second device row appear with `deviceType=MOBILE`, `operatingSystem=iOS`.

Revoke a device:
```bash
curl -s -X DELETE http://localhost:8080/api/v1/devices/{deviceId} \
  -H "Authorization: Bearer $ACCESS"
```

---

## 📊 Swagger UI

Open http://localhost:8080/swagger-ui.html — new tag groups:

- **Authentication** — 5 endpoints (`/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/logout-all`, `/auth/me`)
- **Sessions** — 3 endpoints
- **Devices** — 3 endpoints

---

## ✅ Success Criteria

Phase 3 is complete when **all** of the following hold:

- [x] Migration `V4__auth_sessions_and_devices.sql` applied cleanly.
- [x] `mvn -q clean compile` succeeds.
- [x] `POST /api/v1/auth/login` returns a `TokenResponse` for a verified user.
- [x] `GET /api/v1/auth/me` with the access token returns the principal;
      without it returns `401`.
- [x] `POST /api/v1/auth/refresh` rotates the token; the old refresh is marked
      `ROTATED` in `refresh_tokens`.
- [x] Reusing a rotated refresh token returns `401 refresh-token-reuse` and
      marks all tokens in that session `COMPROMISED`.
- [x] `POST /api/v1/auth/logout` terminates the session; deleting
      `session:{id}` in Redis also blocks the JWT.
- [x] `POST /api/v1/auth/logout-all` terminates every active session for the user.
- [x] `/api/v1/devices` lists device rows with heuristic UA parsing populated.
- [x] Wrong password 5× locks the account (`user.locked_until` set).

---

## 🐛 Troubleshooting

**`Invalid or expired access token` immediately after login**
Almost always a mismatched signing key. In dev the app generates a random key each
boot if `meicrypt.auth.secret` is unset — tokens issued before the previous restart
are invalid. Set `MEICRYPT_JWT_SECRET` (≥ 32 bytes) for a stable key.

**Redis not reachable**
`SessionCacheService` fail-opens (allows requests through) but logs a warning.
Bring Redis up with `docker-compose up -d redis`.

**Account keeps failing to log in with `Account locked`**
Wait 30 minutes or clear the lock manually:
```sql
UPDATE users SET failed_login_attempts=0, locked_until=NULL WHERE email='alice@example.com';
```

---

**Phase 3 complete. Ready for Phase 4 (RBAC Authorization Framework). 🎉**
