# Phase 7 Verification Guide — OIDC Discovery + JWKS

Phase 7 turns MeiCrypt into a **fully-featured OpenID Connect Provider**:
external relying parties can auto-discover the OAuth/OIDC endpoints and
verify the signatures on every access token and ID token independently,
without any shared-secret exchange.

## ✅ Modules Delivered

| Module | Description                                                     | Status |
|--------|-----------------------------------------------------------------|--------|
| 7.1    | `/.well-known/openid-configuration` discovery document (OIDC Discovery 1.0 §3, RFC 8414) | ✓ |
| 7.2    | RS256 signing key registry with rotation + `/.well-known/jwks.json` (RFC 7517/7518) | ✓ |

### New files under `com.meicrypt.identity.oauth.*`

```
oauth/
├── controller/
│   ├── JwksController.java                # GET /.well-known/jwks.json
│   └── OpenIdDiscoveryController.java     # GET /.well-known/openid-configuration
├── dto/
│   ├── JwkDTO.java
│   ├── JwksResponse.java
│   └── OpenIdConfigurationResponse.java
├── entity/
│   ├── OAuthSigningKey.java
│   └── OAuthSigningKeyStatus.java
├── repository/OAuthSigningKeyRepository.java
└── service/OAuthSigningKeyService.java
```

Additional changes:

- `db/migration/V9__oauth_signing_keys.sql` — new `oauth_signing_keys`
  table storing RSA key pairs (base64-encoded PKCS#8 / X.509 SPKI) plus
  lifecycle status (`ACTIVE`, `ROTATED`, `REVOKED`).
- `OAuthTokenGenerator` — upgraded from HS256 to **RS256** and now stamps
  every issued JWT with a `kid` header pointing at the exact signing key.
- `SecurityConfiguration` — `/.well-known/**` exposed publicly (required
  by the OIDC Discovery spec).
- `application.yml` — `meicrypt.auth.issuer` is now a full URL
  (`http://localhost:8080` by default) so the discovery document and
  every `iss` claim advertise a spec-compliant value.
- `springdoc.paths-to-match` — Swagger UI now includes `/oauth2/**` and
  `/.well-known/**`.

---

## 🔐 Security & Protocol Contract

- **Asymmetric signatures** — access tokens & ID tokens are signed with
  RSA-2048 (RS256). Relying parties fetch public keys from
  `/.well-known/jwks.json` — no shared secret is required.
- **Seamless rotation** — `OAuthSigningKeyService.rotate()` generates a
  new ACTIVE key and demotes the previous one to ROTATED so outstanding
  tokens remain verifiable until they expire; both keys are simultaneously
  published in the JWKS document.
- **Revoked keys are hidden** — keys with status `REVOKED` are excluded
  from the JWKS output entirely.
- **Discovery integrity** — the discovery document only advertises
  algorithms, grant types, response types, and PKCE methods that the
  server actually implements; anything MeiCrypt does not support is
  omitted (Jackson drops null fields).
- **Public bootstrap** — both `.well-known` endpoints are `permitAll()`
  and cacheable (`Cache-Control: public, max-age=300`).

---

## 📋 Pre-Verification Checklist

### 1. Infrastructure & migration

```bash
docker-compose up -d                 # meicrypt-postgres + meicrypt-redis
./verify-phase7.sh                   # schema / migrations / compile smoke check
```

Expected: `✅ Phase 7 verification complete.`

### 2. Flyway migration history

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```
Expected trailing row:
```
 9 | oauth signing keys | t
```

### 3. Signing key bootstrap

The first application startup after V9 migrates must insert exactly one
ACTIVE row into `oauth_signing_keys`:

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c \
  "SELECT kid, algorithm, status, created_at FROM oauth_signing_keys ORDER BY created_at DESC;"
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

### Step 1 — Fetch the discovery document

```bash
curl -s "$BASE/.well-known/openid-configuration" | jq
```

Expected keys: `issuer`, `authorization_endpoint`, `token_endpoint`,
`introspection_endpoint`, `revocation_endpoint`, `jwks_uri`,
`response_types_supported=["code"]`, `id_token_signing_alg_values_supported=["RS256"]`,
`code_challenge_methods_supported=["S256"]`.

### Step 2 — Fetch the JWKS document

```bash
curl -s "$BASE/.well-known/jwks.json" | jq
```

Expected: an object with a `keys` array containing at least one entry:
```json
{
  "kty": "RSA",
  "use": "sig",
  "alg": "RS256",
  "kid": "mei-<32 hex chars>",
  "n":   "<base64url modulus>",
  "e":   "AQAB"
}
```

### Step 3 — Verify a real access token against JWKS

Run the Phase 6 walkthrough to obtain `$AT` (a full JWT
access token). Then:

```bash
# Decode the header (unsigned) and confirm alg=RS256 + a kid pointing at JWKS.
echo "$AT" | cut -d. -f1 | base64 -d 2>/dev/null | jq
# Fetch the exact key advertised in the header
KID=$(echo "$AT" | cut -d. -f1 | base64 -d 2>/dev/null | jq -r .kid)
curl -s "$BASE/.well-known/jwks.json" | jq --arg kid "$KID" '.keys[] | select(.kid==$kid)'
```

Optional — verify the signature end-to-end using any JWT library, e.g.
[jwt.io](https://jwt.io) (paste the JWKS response into "Public Key").

### Step 4 — ID token

Phase 6's token walkthrough already returned an `id_token` when the client
requested the `openid` scope. That JWT is now RS256-signed and its
issuer/audience/exp/iat/sub claims match the discovery advertisement:

```bash
echo "$IDT" | cut -d. -f2 | base64 -d 2>/dev/null | jq
```

Expected claims include `iss`, `sub`, `aud=<client_id>`, `exp`, `iat`,
`email`, `email_verified`, `token_type=id_token`, and (when supplied at
`/authorize`) `nonce`.

### Step 5 — Discover-then-consume automation

A tiny sanity script combining both endpoints:

```bash
DISCO=$(curl -s "$BASE/.well-known/openid-configuration")
JWKS_URI=$(echo "$DISCO" | jq -r .jwks_uri)
ALGS=$(echo "$DISCO" | jq -r '.id_token_signing_alg_values_supported | join(",")')
echo "JWKS URI: $JWKS_URI"
echo "Supported id_token algs: $ALGS"
curl -s "$JWKS_URI" | jq '.keys | length'
```

---

## 📊 Swagger UI

http://localhost:8080/swagger-ui.html — a new **OIDC Discovery** tag now
appears alongside the Phase 6 OAuth tags.

---

## ✅ Success Criteria

Phase 7 is complete when **all** of the following hold:

- [x] Migration `V9` applied cleanly (`oauth_signing_keys` table exists).
- [x] Application startup inserts exactly one `ACTIVE` row into
      `oauth_signing_keys` (idempotent on subsequent boots).
- [x] `mvn -q clean compile` succeeds.
- [x] `GET /.well-known/openid-configuration` returns HTTP 200 JSON
      advertising the correct `issuer`, `authorization_endpoint`,
      `token_endpoint`, `introspection_endpoint`, `revocation_endpoint`,
      `jwks_uri`, and `id_token_signing_alg_values_supported=["RS256"]`.
- [x] `GET /.well-known/jwks.json` returns HTTP 200 JSON containing
      at least one key with `kty=RSA`, `alg=RS256`, `use=sig`, a `kid`,
      a base64url-encoded `n` (~342 chars for RSA-2048), and `e=AQAB`.
- [x] Phase 6 tokens (`access_token`, `id_token`) issued after this
      deployment are RS256-signed, carry a `kid` header matching a JWKS
      entry, and validate successfully against the published public key.
- [x] Both `.well-known` endpoints are publicly reachable — no bearer
      token required.
- [x] Signing key rotation (`OAuthSigningKeyService.rotate()`) demotes
      the previous key to `ROTATED` and continues to publish it in JWKS
      until it is administratively `REVOKED`.

---

## 🐛 Troubleshooting

**`iss` mismatch in the discovery document**
Set `MEICRYPT_ISSUER` (or `meicrypt.auth.issuer` in `application.yml`)
to the exact URL reachable by relying parties, e.g. `https://auth.acme.com`.
The value MUST NOT contain a trailing slash.

**`/.well-known/*` returns 401**
The endpoints are `permitAll()` in `SecurityConfiguration`. If a proxy
enforces authentication upstream, exempt these paths there too.

**JWT verification failing for a token issued *before* rotation**
Confirm the key is still `ROTATED` (not `REVOKED`) and check that the
JWKS response contains its `kid`. Rotated keys stay in the document
until they are explicitly revoked.

**Modulus `n` in JWKS looks 1 char longer than expected**
Some libraries reject a leading zero byte. `JwksController` already
strips the two's-complement sign byte before base64url encoding; if
your library still complains, verify you are hitting the MeiCrypt endpoint
and not a stale cached copy.

---

## 🚀 Quick verify (one command)

```bash
./verify-phase7.sh
```

---

**Phase 7 complete. Ready for Phase 8 (SSO Federation) + Phase 9 (MFA). 🎉**
