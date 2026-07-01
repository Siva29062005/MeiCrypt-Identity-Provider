# Phase 6 Verification Guide — OAuth2 Authorization Server (Core)

Phase 6 turns MeiCrypt into a fully spec-compliant **OAuth 2.0 Authorization Server**.
Any client application registered in Phase 5 can now:

1. Send its end-users through the `/oauth2/authorize` endpoint,
2. Exchange the returned `code` (with PKCE) at `/oauth2/token` for a
   signed JWT `access_token` + rotating `refresh_token`,
3. Introspect / revoke those tokens via `/oauth2/introspect` and
   `/oauth2/revoke`.

## ✅ Modules Delivered

| Module | Description                                                     | Status |
|--------|-----------------------------------------------------------------|--------|
| 6.1    | `authorization_code` flow (**PKCE S256 mandatory**), `refresh_token` rotation with reuse detection, RFC 7009 revocation | ✓ |
| 6.2    | Scope enforcement — requested scopes must be a subset of the client's registered scopes; scopes may only be narrowed on refresh | ✓ |
| (bonus)| RFC 7662 token introspection endpoint                            | ✓ |

### New files under `com.meicrypt.identity.oauth.*`

```
oauth/
├── controller/
│   ├── OAuthAuthorizationController.java   # GET /oauth2/authorize
│   ├── OAuthTokenController.java           # POST /oauth2/token
│   ├── OAuthRevocationController.java      # POST /oauth2/revoke   (RFC 7009)
│   └── OAuthIntrospectionController.java   # POST /oauth2/introspect (RFC 7662)
├── dto/
│   ├── OAuthTokenResponse.java
│   ├── OAuthErrorResponse.java
│   ├── AuthorizationCodeResponse.java
│   └── IntrospectionResponse.java
├── entity/
│   ├── OAuthAuthorizationCode.java
│   ├── OAuthAccessToken.java
│   ├── OAuthRefreshToken.java
│   └── OAuthRefreshTokenStatus.java
├── exception/OAuthException.java
├── repository/
│   ├── OAuthAuthorizationCodeRepository.java
│   ├── OAuthAccessTokenRepository.java
│   └── OAuthRefreshTokenRepository.java
└── service/
    ├── PkceValidator.java
    ├── ScopeService.java
    ├── OAuthTokenGenerator.java
    ├── OAuthAuthorizationService.java
    ├── OAuthTokenService.java
    └── OAuthIntrospectionService.java
```

Additional changes:

- `db/migration/V8__oauth_authorization_codes_and_tokens.sql` — three new tables:
  `oauth_authorization_codes`, `oauth_access_tokens`, `oauth_refresh_tokens`.
- `SecurityConfiguration.java` — `/oauth2/**` endpoints exposed publicly (RFC-compliant).
  The Phase 3 JWT filter still runs, so `/oauth2/authorize` sees the logged-in
  user when a Phase 3 access token is presented.
- `GlobalExceptionHandler.java` — new `OAuthException` handler returning
  RFC 6749 §5.2 JSON error bodies with proper `Cache-Control` / `WWW-Authenticate`
  headers.

---

## 🔐 Security & Protocol Contract

- **PKCE mandatory** — `code_challenge_method=S256` only.  `plain` is rejected.
- **Confidential clients** authenticate at `/oauth2/token` via HTTP Basic
  (preferred) or form body — `client_secret` is verified against the BCrypt
  hash stored in Phase 5.
- **Public clients** (`SPA`, `MOBILE`) MUST NOT send a `client_secret`
  and MUST supply a PKCE verifier.
- **Refresh token rotation** — every use rotates the refresh token.
  Presenting a rotated/revoked/expired refresh token trips the reuse detector
  and revokes **every** active access + refresh token for that
  (user, client) pair.
- **Scopes cannot be broadened** on refresh — only narrowed.
- **Multi-tenant isolation** — a user can only authorize clients registered
  inside their own organization (`OAuthAuthorizationService` enforces this).
- **Token registry** — every access token JWT is also persisted (SHA-256 hash
  + `jti`) so revocation and introspection are instant, even before the JWT
  expires.
- **Storage discipline** — plaintext codes / refresh tokens are never persisted;
  only SHA-256 hashes hit the database. `client_secret` continues to be BCrypt.

---

## 📋 Pre-Verification Checklist

### 1. Infrastructure & migration

```bash
docker-compose up -d                 # meicrypt-postgres + meicrypt-redis
./verify-phase6.sh                   # schema / migrations / compile smoke check
```

Expected: `✅ Phase 6 schema verification passed.`

### 2. Flyway migration history

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```
Expected trailing row:
```
 8 | oauth authorization codes and tokens | t
```

### 3. Tables exist

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "\dt" | \
  grep -E 'oauth_authorization_codes|oauth_access_tokens|oauth_refresh_tokens'
```

### 4. Compile

```bash
mvn -q -DskipTests clean compile
```

---

## 🧪 End-to-end walkthrough

Set common variables (mirroring Phases 4/5):

```bash
BASE=http://localhost:8080
ORG=00000000-0000-0000-0000-000000000001
```

### Step 1 — Log Alice into the platform (Phase 3)

```bash
ACCESS=$(curl -s -X POST "$BASE/api/v1/auth/login" \
              -H 'Content-Type: application/json' \
              -d "{\"organizationId\":\"$ORG\",
                   \"email\":\"alice@example.com\",
                   \"password\":\"SecurePass123!\"}" | jq -r '.accessToken')
```

### Step 2 — Register a confidential client (Phase 5)

```bash
CREATED=$(curl -s -X POST "$BASE/api/v1/organizations/$ORG/applications" \
  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
  -d '{
    "name": "MeiCrypt CRM (OAuth Test)",
    "applicationType": "WEB",
    "redirectUris": ["http://localhost:3000/oauth/callback"],
    "scopes": ["openid","profile","email","crm.read","crm.write"]
  }')
CLIENT_ID=$(echo "$CREATED" | jq -r '.application.clientId')
CLIENT_SECRET=$(echo "$CREATED" | jq -r '.credentials.clientSecret')
REDIRECT="http://localhost:3000/oauth/callback"
```

### Step 3 — Generate a PKCE pair

```bash
VERIFIER=$(openssl rand -base64 48 | tr '+/' '-_' | tr -d '=' | head -c 64)
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | \
            openssl base64 | tr '+/' '-_' | tr -d '=')
STATE=$(openssl rand -hex 8)
echo "verifier=$VERIFIER"
echo "challenge=$CHALLENGE"
```

### Step 4 — Call `/oauth2/authorize` as Alice

We call the endpoint with `-i` to see the 302 redirect; we DO NOT follow it,
we parse the `code` out of the `Location` header:

```bash
LOC=$(curl -sSI -o /dev/null -w '%{redirect_url}' \
      -H "Authorization: Bearer $ACCESS" \
      "$BASE/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT&scope=openid%20profile%20email%20crm.read&state=$STATE&code_challenge=$CHALLENGE&code_challenge_method=S256")
echo "302 -> $LOC"
CODE=$(echo "$LOC" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
[[ -n "$CODE" ]] && echo "code=$CODE"
```

Expected: The URL echoes back the `state` you supplied and contains a fresh
`code` parameter.

### Step 5 — Exchange code for tokens at `/oauth2/token`

```bash
TOKENS=$(curl -s -X POST "$BASE/oauth2/token" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=$CODE" \
  --data-urlencode "redirect_uri=$REDIRECT" \
  --data-urlencode "code_verifier=$VERIFIER")
echo "$TOKENS" | jq
AT=$(echo "$TOKENS" | jq -r '.access_token')
RT=$(echo "$TOKENS" | jq -r '.refresh_token')
IDT=$(echo "$TOKENS" | jq -r '.id_token')
```

Expected: HTTP 200 with a JSON body containing `access_token`, `token_type=Bearer`,
`expires_in`, `refresh_token`, `scope`, and (because `openid` was requested)
`id_token`. Response headers include `Cache-Control: no-store`.

### Step 6 — Replay protection

Retrying the same code MUST fail with `invalid_grant`:

```bash
curl -s -o /dev/null -w 'replay=%{http_code}\n' -X POST "$BASE/oauth2/token" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=$CODE" \
  --data-urlencode "redirect_uri=$REDIRECT" \
  --data-urlencode "code_verifier=$VERIFIER"
# expected: replay=400
```

### Step 7 — Introspect the access token

```bash
curl -s -X POST "$BASE/oauth2/introspect" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "token=$AT" | jq
```

Expected: `active=true` with `scope`, `sub`, `client_id`, `iss`, `exp`, `iat`.

### Step 8 — Rotate the refresh token

```bash
REFRESHED=$(curl -s -X POST "$BASE/oauth2/token" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=$RT" \
  --data-urlencode "scope=openid profile")
echo "$REFRESHED" | jq
NEW_RT=$(echo "$REFRESHED" | jq -r '.refresh_token')
[ "$NEW_RT" != "$RT" ] && echo "✅ refresh rotated"
```

Reuse of the OLD refresh token must now trigger the compromise handler:

```bash
curl -s -X POST "$BASE/oauth2/token" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=$RT" | jq
# expected: {"error":"invalid_grant","error_description":"Refresh token reuse detected - all tokens revoked"}
```

### Step 9 — Revoke a token (RFC 7009)

```bash
curl -s -o /dev/null -w 'revoke=%{http_code}\n' -X POST "$BASE/oauth2/revoke" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "token=$NEW_RT"
# expected: revoke=200

# Introspection now returns active=false
curl -s -X POST "$BASE/oauth2/introspect" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "token=$NEW_RT" | jq
```

### Step 10 — Scope enforcement (Module 6.2)

Requesting a scope that the client never registered must produce an
`invalid_scope` redirect:

```bash
curl -sSI -o /dev/null -w '%{redirect_url}\n' \
     -H "Authorization: Bearer $ACCESS" \
     "$BASE/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT&scope=admin.everything&state=$STATE&code_challenge=$CHALLENGE&code_challenge_method=S256"
# expected: ...?error=invalid_scope&error_description=...
```

### Step 11 — Public client (PKCE-only, no secret)

```bash
SPA=$(curl -s -X POST "$BASE/api/v1/organizations/$ORG/applications" \
  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
  -d '{
    "name": "MeiCrypt SPA Demo",
    "applicationType": "SPA",
    "redirectUris": ["http://localhost:5173/callback"],
    "scopes": ["openid","profile","email"]
  }')
SPA_ID=$(echo "$SPA" | jq -r '.application.clientId')
```

Confidential-style token exchange (with client_secret) MUST fail with
`invalid_client`:

```bash
curl -s -X POST "$BASE/oauth2/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "client_id=$SPA_ID" \
  --data-urlencode "client_secret=must-not-be-sent" \
  --data-urlencode "code=whatever" \
  --data-urlencode "redirect_uri=http://localhost:5173/callback" \
  --data-urlencode "code_verifier=whatever" | jq
# expected: {"error":"invalid_client","error_description":"Public clients must not present a client_secret"}
```

---

## 📊 Swagger UI

http://localhost:8080/swagger-ui.html — four new tag groups:

- **OAuth2 Authorization**
- **OAuth2 Token**
- **OAuth2 Revocation**
- **OAuth2 Introspection**

---

## ✅ Success Criteria

Phase 6 is complete when **all** of the following hold:

- [x] Migration `V8` applied cleanly.
- [x] `oauth_authorization_codes`, `oauth_access_tokens`, `oauth_refresh_tokens`
      tables exist.
- [x] `mvn -q clean compile` succeeds.
- [x] `GET /oauth2/authorize` with a valid Phase 3 JWT + PKCE + registered
      redirect_uri returns **HTTP 302** to the client's redirect URI with a
      `code` and preserved `state`.
- [x] `POST /oauth2/token` exchanges the code + PKCE verifier for a signed
      JWT `access_token`, opaque `refresh_token`, and (when `openid` scope was
      granted) an `id_token`.
- [x] Replayed authorization codes return **HTTP 400 / `invalid_grant`**.
- [x] Public clients cannot present a `client_secret`; confidential clients
      cannot omit one.
- [x] Refresh token rotation invalidates the old refresh token; reuse of a
      rotated refresh token revokes the entire (user, client) token family.
- [x] Requested scopes must be a subset of the client's registered scopes;
      refresh can only narrow scope, never broaden it.
- [x] `/oauth2/revoke` returns **HTTP 200** and instantly invalidates the
      token in the registry.
- [x] `/oauth2/introspect` returns `{active: false}` for revoked/expired
      tokens (and for tokens owned by a different client).
- [x] Cross-tenant authorization is impossible — the user's `organization_id`
      must match the client's `organization_id`.

---

## 🐛 Troubleshooting

**302 to `error=invalid_request` when calling /oauth2/authorize**
Double-check that the exact `redirect_uri` is registered on the client
(Phase 5 stores them verbatim, exact-match validated). Trailing slashes and
scheme mismatches will trigger this.

**"invalid_grant / code_verifier does not match code_challenge"**
The verifier must be the exact same string you fed into SHA-256 → base64url
(no padding). Run:
```bash
printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | \
    openssl base64 | tr '+/' '-_' | tr -d '='
```
and confirm the result matches the challenge you originally sent to
`/authorize`.

**"invalid_client" on /token even with correct secret**
The client must be `ACTIVE`. Check:
```bash
$PG -c "SELECT client_id, status, application_type FROM client_applications;"
```

**Refresh token immediately fails as `invalid_grant`**
That means a previous refresh already rotated it. Use the newest refresh
token returned by the most recent `/token` call.

---

## 🚀 Quick verify (one command)

```bash
./verify-phase6.sh
```

---

**Phase 6 complete. Ready for Phase 7 (OIDC discovery + JWKS). 🎉**
