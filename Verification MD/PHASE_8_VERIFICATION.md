# Phase 8 Verification Guide — SSO Federation & Single Logout

Phase 8 completes the multi-application session story for the MeiCrypt
Identity Platform:

- **Module 8.1 (SSO Shared Sessions)** — a single Phase-3 login seeds any
  number of OAuth client applications inside the same organization without
  re-authenticating the user.
- **Module 8.2 (Single Logout)** — one logout at the identity provider
  tears down every relying party through OIDC RP-Initiated Logout and
  Back-Channel Logout notifications.

---

## ✅ Modules Delivered

| Module | Description | Status |
|--------|-------------|--------|
| 8.1 | SSO session anchored to Phase-3 `user_sessions`; participant tracking per OAuth `/authorize` call; introspection endpoint at `GET /api/v1/sso/session` | ✓ |
| 8.2 | `GET /oauth2/logout` (OIDC RP-Initiated Logout 1.0); Back-Channel Logout dispatcher signing `logout_token` JWTs with RS256; cascading revocation of every OAuth artifact bound to the session | ✓ |

### New source layout

```
sso/
├── entity/
│   ├── SsoSession.java
│   ├── SsoSessionStatus.java
│   └── SsoSessionParticipant.java
├── repository/
│   ├── SsoSessionRepository.java
│   └── SsoSessionParticipantRepository.java
├── service/
│   ├── SsoSessionService.java
│   └── BackchannelLogoutService.java
├── controller/
│   ├── SsoSessionController.java        # GET /api/v1/sso/session
│   └── OAuthLogoutController.java       # GET /oauth2/logout
├── config/AsyncConfiguration.java       # @EnableAsync + dedicated pool
├── dto/{SsoSessionDTO, LogoutResponse}.java
└── exception/SsoSessionNotFoundException.java
```

Additional cross-module changes:

- `db/migration/V10__sso_sessions_and_participants.sql` — new
  `sso_sessions` and `sso_session_participants` tables plus a new
  `client_applications.backchannel_logout_uri` column.
- `AuthenticationService` — login now bootstraps an SSO session; every
  session-terminating path (`logout`, `logoutSession`, refresh-reuse)
  cascades OAuth token revocation via
  `OAuthRefreshTokenRepository.revokeAllForSession` and triggers the
  Back-Channel Logout dispatcher.
- `OAuthAuthorizationService` — each successful `/oauth2/authorize`
  invocation writes an `sso_session_participants` row for the target
  client (idempotent).
- `ClientApplication` entity + create/update DTOs + mapper + service now
  carry the optional `backchannelLogoutUri`.
- `SecurityConfiguration` — `/oauth2/logout` is `permitAll()` (RP-Initiated
  Logout requires public access so unauthenticated browsers can still be
  redirected home).
- `OpenIdConfigurationResponse` / `OpenIdDiscoveryController` — the
  discovery document now advertises `end_session_endpoint`,
  `backchannel_logout_supported=true` and
  `backchannel_logout_session_supported=true`.
- `GlobalExceptionHandler` — new RFC-7807 mapping for
  `SsoSessionNotFoundException`.

---

## 🔐 Security & Protocol Contract

- **One SSO per Phase-3 session** — strict 1:1 with `user_sessions`,
  enforced by a UNIQUE constraint on `sso_sessions.user_session_id`.
- **Participant registration is idempotent** — repeated `/authorize`
  calls from the same client update `last_authorized_at` and
  `last_scope` but never duplicate rows.
- **Cascade revocation on logout** — `AuthenticationService.terminateSession`
  now performs an atomic:
  1. `refresh_tokens` (Phase 3) → REVOKED for the session
  2. `oauth_refresh_tokens` (Phase 6) → REVOKED for the session
  3. `user_sessions.status` → TERMINATED
  4. `sso_sessions.status` → TERMINATED
  5. Back-Channel Logout POSTs dispatched asynchronously
- **`logout_token` per OIDC BCL 1.0 §2.4** — RS256-signed JWT with
  `typ=logout+jwt`, `events` claim, `sid`, `iss`, `aud=client_id`, `iat`,
  and `jti`. Signature verifiable against the same JWKS document
  Phase 7 already publishes.
- **Redirect-target integrity** — `post_logout_redirect_uri` MUST be
  registered against the client (`client_application_logout_uris`); the
  server responds 400 `invalid_request` otherwise, never following an
  unregistered URL.
- **Async isolation** — Back-Channel Logout runs on a dedicated
  `backchannelLogoutExecutor` (4 core / 16 max threads); slow relying
  parties can never starve interactive request handlers.

---

## 📋 Pre-Verification Checklist

### 1. Infrastructure & migrations

```bash
docker-compose up -d                # meicrypt-postgres + meicrypt-redis
./verify-phase8.sh                  # schema, wiring, compile smoke check
```

Expected trailing output: `✅ Phase 8 verification complete.`

### 2. Flyway history

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```
Expected trailing row:
```
 10 | sso sessions and participants | t
```

### 3. Schema smoke

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "\d sso_sessions"
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "\d sso_session_participants"
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT column_name FROM information_schema.columns WHERE table_name='client_applications' AND column_name='backchannel_logout_uri';"
```

### 4. Compile

```bash
mvn -q -DskipTests clean compile
```

---

## 🧪 End-to-end walkthrough

```bash
BASE=http://localhost:8080
```

### Step 1 — Log in and read the SSO session

```bash
LOGIN=$(curl -s -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@acme.com","password":"...","organizationId":"...","deviceName":"demo"}')
AT=$(echo "$LOGIN" | jq -r .accessToken)

curl -s "$BASE/api/v1/sso/session" -H "Authorization: Bearer $AT" | jq
```

Expected: an object with `ssoId`, `userSessionId`, `status=ACTIVE`, and
an initially empty `participants` array.

### Step 2 — Authorize two client applications through the same session

Run the Phase 6 walkthrough twice against different `client_id`s. After
each `/oauth2/authorize` call, re-read `/api/v1/sso/session` and observe
that `participants` grows accordingly and `lastScope` reflects the last
grant. Rows in the DB:

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT c.client_id, p.first_authorized_at, p.last_authorized_at, p.last_scope
   FROM sso_session_participants p
   JOIN client_applications c ON c.id = p.client_application_id
   ORDER BY p.first_authorized_at;"
```

### Step 3 — Discover the end-session endpoint

```bash
curl -s "$BASE/.well-known/openid-configuration" | jq \
    '{end_session_endpoint,
      backchannel_logout_supported,
      backchannel_logout_session_supported}'
```

Expected:

```json
{
  "end_session_endpoint": "http://localhost:8080/oauth2/logout",
  "backchannel_logout_supported": true,
  "backchannel_logout_session_supported": true
}
```

### Step 4 — RP-Initiated Logout (redirect flow)

Point a browser at (or `curl -I` with a valid session cookie/bearer):

```
GET /oauth2/logout?client_id=<client_id>
                  &post_logout_redirect_uri=<registered_uri>
                  &state=xyz
```

Expected:

- HTTP 302 → `<registered_uri>?state=xyz`
- `sso_sessions.status = TERMINATED`, `terminated_at` populated
- `user_sessions.status = TERMINATED`
- Every `oauth_refresh_tokens` row for that session is now `REVOKED`
- Every participating client with a `backchannel_logout_uri` has received
  (asynchronously) a signed `logout_token` — the corresponding
  `sso_session_participants.logout_notification_state` transitions
  `NULL → SENT` (or `FAILED` when the RP is unreachable, `SKIPPED` when
  the client did not register a URI).

### Step 5 — Confirm token cascade

```bash
# After logout, the Phase-6 access_token issued in step 2 must be dead.
curl -s -o /dev/null -w '%{http_code}\n' \
  "$BASE/api/v1/sso/session" -H "Authorization: Bearer $AT"
```

Expected: `401`.

### Step 6 — Register a Back-Channel Logout URI on a client

```bash
curl -s -X PATCH \
  "$BASE/api/v1/organizations/$ORG_ID/applications/$APP_ID" \
  -H "Authorization: Bearer $ADMIN_AT" \
  -H 'Content-Type: application/json' \
  -d '{"backchannelLogoutUri":"https://client.example.com/logout"}' | jq .backchannelLogoutUri
```

Expected: `"https://client.example.com/logout"`.

---

## 📊 Swagger UI

http://localhost:8080/swagger-ui.html — two new tags appear:

- **SSO Federation** — `GET /api/v1/sso/session`
- **OAuth2 Logout** — `GET /oauth2/logout`

The **OIDC Discovery** tag response now includes
`end_session_endpoint` and the two `backchannel_*` capability flags.

---

## ✅ Success Criteria

Phase 8 is complete when **all** of the following hold:

- [x] Migration `V10` applied cleanly (`sso_sessions`,
      `sso_session_participants`, and
      `client_applications.backchannel_logout_uri` all exist).
- [x] `mvn -q clean compile` succeeds.
- [x] Every successful Phase-3 login inserts exactly one `ACTIVE` row
      into `sso_sessions` with a fresh 32-byte URL-safe `sso_id`.
- [x] Every successful `/oauth2/authorize` call inserts (or
      idempotently updates) an `sso_session_participants` row.
- [x] `GET /api/v1/sso/session` returns the active SSO session with the
      participant list for the caller.
- [x] `GET /.well-known/openid-configuration` advertises
      `end_session_endpoint=<issuer>/oauth2/logout`,
      `backchannel_logout_supported=true`, and
      `backchannel_logout_session_supported=true`.
- [x] `GET /oauth2/logout` terminates the caller's Phase-3 session,
      cascades revocation to every `oauth_refresh_tokens` row for that
      session, and asynchronously dispatches signed `logout_token`s to
      every participant with a registered `backchannel_logout_uri`.
- [x] `post_logout_redirect_uri` that is not registered against the
      client is rejected 400 `invalid_request`; the server never
      follows an unregistered URL.
- [x] Anonymous callers of `/oauth2/logout` still receive a 302 back
      to the registered redirect (or 204 if none supplied) — required
      by OIDC RP-Initiated Logout 1.0 §3.
- [x] `SecurityConfiguration` exposes `/oauth2/logout` as `permitAll()`.

---

## 🐛 Troubleshooting

**Back-channel POSTs never arrive**
Ensure the client's `backchannel_logout_uri` is publicly reachable from
the identity provider — the async dispatcher does not retry on TCP
timeout. Check
`SELECT logout_notification_state, logout_notification_error FROM sso_session_participants;`.

**`/oauth2/logout` returns 400 invalid_request**
The `post_logout_redirect_uri` must exactly match a row in
`client_application_logout_uris` (Phase 5). Whitespace, trailing slash,
and scheme are all significant.

**Access token still works after logout**
Access tokens are RS256 JWTs and remain cryptographically valid until
their `exp`. Phase 3's `SessionCacheService` blacklists them via Redis;
if you are running without Redis, JWT verification will still fail once
the underlying `user_sessions` row is TERMINATED because the Phase-3
filter re-validates the session on every request.

**`logout_token` fails RP signature verification**
The token is signed with the current ACTIVE key from
`oauth_signing_keys`. Ensure your RP fetches JWKS on every logout attempt
(or caches with a TTL ≤ 5 minutes) so newly-rotated keys are picked up.

---

## 🚀 Quick verify (one command)

```bash
./verify-phase8.sh
```

---

**Phase 8 complete. Ready for Phase 9 (Advanced MFA — TOTP + WebAuthn). 🎉**
