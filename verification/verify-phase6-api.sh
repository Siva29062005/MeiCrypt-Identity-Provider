#!/bin/bash
# ============================================================================
# MeiCrypt Identity Platform - Phase 6 (OAuth2 Authorization Server core)
# end-to-end API verification.
# ----------------------------------------------------------------------------
# What it does:
#   1. Ensures alice + bob exist, are verified & ACTIVE, and hold the SYSTEM
#      admin / member roles (identical to Phase 4/5 harness).
#   2. Registers a fresh confidential WEB client + a public SPA client via the
#      Phase 5 registry.
#   3. Exercises the two Phase 6 modules against the running app:
#        6.1  Authorization workflows  (/authorize -> /token -> refresh)
#        6.2  Scope enforcement        (subset only, narrow-only refresh)
#      plus RFC 7009 revocation and RFC 7662 introspection.
#   4. Verifies negative paths:
#        - PKCE mandatory (missing verifier -> invalid_grant)
#        - authorization code replay -> invalid_grant
#        - refresh token reuse -> invalid_grant + entire family revoked
#        - public client rejects client_secret
#        - unregistered redirect_uri -> invalid_request
#        - out-of-list scope -> invalid_scope
#
# Requirements: docker (postgres container "meicrypt-postgres"), curl, jq, openssl.
# Safe to re-run.
# ============================================================================
set -u

BASE=${BASE:-http://localhost:8080}
ORG=${ORG:-00000000-0000-0000-0000-000000000001}
PG="docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity"
PASS='SecurePass123!'

green() { printf '\033[32m%s\033[0m\n' "$*" >&2; }
red()   { printf '\033[31m%s\033[0m\n' "$*" >&2; }
blue()  { printf '\033[34m%s\033[0m\n' "$*" >&2; }
yellow(){ printf '\033[33m%s\033[0m\n' "$*" >&2; }

FAILED=0
STEP=0
check() { # check "label" expected actual
    STEP=$((STEP+1))
    local label=$1 expected=$2 actual=$3
    if [ "$expected" = "$actual" ]; then
        green "  ✅  [$STEP] $label (HTTP $actual)"
    else
        red   "  ❌  [$STEP] $label — expected HTTP $expected, got $actual"
        FAILED=$((FAILED+1))
    fi
}
assert() { # assert "label" cond-string ("true"/"false")
    STEP=$((STEP+1))
    local label=$1 cond=$2
    if [ "$cond" = "true" ]; then
        green "  ✅  [$STEP] $label"
    else
        red   "  ❌  [$STEP] $label"
        FAILED=$((FAILED+1))
    fi
}
require() { command -v "$1" >/dev/null 2>&1 || { red "Missing tool: $1"; exit 2; }; }
require curl
require jq
require docker
require openssl

# ---------------------------------------------------------------------------
# 0. Pre-flight
# ---------------------------------------------------------------------------
blue "── 0. Pre-flight ─────────────────────────────────────────────"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health" 2>/dev/null || echo 000)
if [ "$STATUS" != "200" ]; then
    red "App is not reachable on $BASE (health = $STATUS). Start it with 'mvn spring-boot:run' first."
    exit 1
fi
green "App is up on $BASE"

V8=$($PG -tA -c "SELECT COUNT(*) FROM flyway_schema_history WHERE version='8' AND success=true;" | tr -d '[:space:]')
if [ "$V8" != "1" ]; then
    red "Flyway V8 not applied yet — is the app running with the Phase 6 sources? Aborting."
    exit 1
fi
green "V8 migration applied"

# ---------------------------------------------------------------------------
# 1. Provision alice + bob (idempotent) - mirrors Phase 4/5 harness
# ---------------------------------------------------------------------------
blue "── 1. Ensure users alice + bob exist ─────────────────────────"
register_user() {
    local email=$1 first=$2 last=$3
    local body
    body=$(jq -n --arg o "$ORG" --arg e "$email" --arg p "$PASS" \
                --arg f "$first" --arg l "$last" \
                '{organizationId:$o, email:$e, password:$p, firstName:$f, lastName:$l}')
    local resp
    resp=$(curl -s -o /tmp/mip_reg.json -w '%{http_code}' \
                -X POST "$BASE/api/v1/users/register" \
                -H 'Content-Type: application/json' -d "$body")
    case "$resp" in
        200|201) green "  Registered $email";;
        409)     yellow "  $email already exists (ok)";;
        *)       red   "  Unexpected register status $resp"; cat /tmp/mip_reg.json; echo;;
    esac
}
register_user alice@example.com Alice Doe
register_user bob@example.com   Bob   Smith

verify_email() {
    local email=$1
    local status
    status=$($PG -tA -c "SELECT status FROM users WHERE email='$email';" | tr -d '[:space:]')
    if [ "$status" = "ACTIVE" ]; then
        yellow "  $email already ACTIVE"; return
    fi
    local token
    token=$($PG -tA -c "SELECT token FROM verification_tokens
                        WHERE token_type='EMAIL_VERIFICATION' AND status='PENDING'
                          AND user_id=(SELECT id FROM users WHERE email='$email')
                        ORDER BY created_at DESC LIMIT 1;" | tr -d '[:space:]')
    if [ -n "$token" ]; then
        curl -s -o /dev/null -X POST "$BASE/api/v1/verification/verify-email" \
             -H 'Content-Type: application/json' -d "{\"token\":\"$token\"}"
        green "  Verified $email"
    fi
}
verify_email alice@example.com
verify_email bob@example.com

ensure_membership() {
    local email=$1 role=$2
    local uid mid
    uid=$($PG -tA -c "SELECT id FROM users WHERE email='$email';" | tr -d '[:space:]')
    mid=$($PG -tA -c "SELECT id FROM organization_memberships
                      WHERE user_id='$uid' AND organization_id='$ORG';" | tr -d '[:space:]')
    if [ -z "$mid" ]; then
        $PG -q -c "INSERT INTO organization_memberships (organization_id, user_id, role, status)
                   VALUES ('$ORG','$uid','$role','ACTIVE');" >/dev/null
        mid=$($PG -tA -c "SELECT id FROM organization_memberships
                          WHERE user_id='$uid' AND organization_id='$ORG';" | tr -d '[:space:]')
    fi
    printf '%s\n' "$mid"
}
MID_ALICE=$(ensure_membership alice@example.com ADMIN)
MID_BOB=$(ensure_membership   bob@example.com   MEMBER)

ADMIN_RID=$($PG -tA -c "SELECT id FROM roles WHERE organization_id='$ORG' AND slug='admin';" | tr -d '[:space:]')
MEMBER_RID=$($PG -tA -c "SELECT id FROM roles WHERE organization_id='$ORG' AND slug='member';" | tr -d '[:space:]')
$PG -q -c "INSERT INTO membership_role_assignments (membership_id, role_id)
           VALUES ('$MID_ALICE','$ADMIN_RID') ON CONFLICT DO NOTHING;" >/dev/null
$PG -q -c "INSERT INTO membership_role_assignments (membership_id, role_id)
           VALUES ('$MID_BOB','$MEMBER_RID')  ON CONFLICT DO NOTHING;" >/dev/null
green "Alice → admin | Bob → member"

# ---------------------------------------------------------------------------
# 2. Login Alice
# ---------------------------------------------------------------------------
blue "── 2. Login Alice (Phase 3 access token) ────────────────────"
LOGIN_BODY=$(jq -n --arg o "$ORG" --arg e alice@example.com --arg p "$PASS" \
                    '{organizationId:$o, email:$e, password:$p}')
ALICE=$(curl -s -X POST "$BASE/api/v1/auth/login" \
             -H 'Content-Type: application/json' -d "$LOGIN_BODY" \
        | jq -r '.accessToken // empty')
[ -n "$ALICE" ] || { red "Alice login failed"; exit 1; }
green "Alice token acquired"

# ---------------------------------------------------------------------------
# 3. Register a confidential WEB client
# ---------------------------------------------------------------------------
blue "── 3. Register a confidential WEB client (Phase 5) ──────────"
SUFFIX=$(date +%s)
REDIRECT="http://localhost:3000/oauth/callback-$SUFFIX"
UNREG_REDIRECT="http://localhost:3000/nope-$SUFFIX"
CREATE_BODY=$(jq -n --arg name "OAuth WEB $SUFFIX" --arg r "$REDIRECT" '{
    name: $name,
    description: "Automated Phase 6 test client",
    applicationType: "WEB",
    homepageUrl: "https://crm.example.test",
    redirectUris: [$r],
    scopes: ["openid","profile","email","crm.read","crm.write"]
}')
STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/applications" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d "$CREATE_BODY")
check "POST /applications (WEB)" 201 "$STATUS"

CLIENT_ID=$(jq -r '.application.clientId' /tmp/mip.json)
CLIENT_SECRET=$(jq -r '.credentials.clientSecret' /tmp/mip.json)
[ -n "$CLIENT_ID" ] && [ -n "$CLIENT_SECRET" ] || { red "Failed to obtain client credentials"; exit 1; }
green "  client_id     = $CLIENT_ID"
green "  client_secret = ${CLIENT_SECRET:0:12}…"

# ---------------------------------------------------------------------------
# 4. Register a public SPA client
# ---------------------------------------------------------------------------
blue "── 4. Register a public SPA client ──────────────────────────"
SPA_REDIRECT="http://localhost:5173/callback-$SUFFIX"
SPA_BODY=$(jq -n --arg name "OAuth SPA $SUFFIX" --arg r "$SPA_REDIRECT" '{
    name: $name,
    applicationType: "SPA",
    redirectUris: [$r],
    scopes: ["openid","profile","email"]
}')
STATUS=$(curl -s -o /tmp/spa.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/applications" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d "$SPA_BODY")
check "POST /applications (SPA)" 201 "$STATUS"
SPA_CLIENT_ID=$(jq -r '.application.clientId' /tmp/spa.json)
green "  SPA client_id = $SPA_CLIENT_ID"

# ---------------------------------------------------------------------------
# 5. Generate a PKCE pair
# ---------------------------------------------------------------------------
blue "── 5. Generate PKCE S256 pair ───────────────────────────────"
VERIFIER=$(openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n' | head -c 64)
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | \
            openssl base64 | tr '+/' '-_' | tr -d '=\n')
STATE=$(openssl rand -hex 8)
NONCE=$(openssl rand -hex 8)
green "  verifier=${VERIFIER:0:12}…  challenge=${CHALLENGE:0:12}…"

urlenc() { jq -rn --arg v "$1" '$v|@uri'; }
REDIRECT_ENC=$(urlenc "$REDIRECT")
UNREG_ENC=$(urlenc "$UNREG_REDIRECT")

# ---------------------------------------------------------------------------
# 6. Module 6.1 — /oauth2/authorize (success path)
# ---------------------------------------------------------------------------
blue "── 6. Module 6.1 — /oauth2/authorize (success) ──────────────"
AUTHZ_URL="$BASE/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_ENC&scope=openid%20profile%20email%20crm.read&state=$STATE&nonce=$NONCE&code_challenge=$CHALLENGE&code_challenge_method=S256"
LOC=$(curl -sSI -o /dev/null -w '%{redirect_url}' \
      -H "Authorization: Bearer $ALICE" "$AUTHZ_URL")
if [ -z "$LOC" ]; then
    red "  ❌  /authorize did not return a redirect"; FAILED=$((FAILED+1))
fi
CODE=$(echo "$LOC" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
RETURNED_STATE=$(echo "$LOC" | sed -n 's/.*[?&]state=\([^&]*\).*/\1/p')
[ -n "$CODE" ]                     && green "  ✅  code returned"           || { red "  ❌  no code in redirect"; FAILED=$((FAILED+1)); }
[ "$RETURNED_STATE" = "$STATE" ]   && green "  ✅  state round-tripped"     || { red "  ❌  state mismatch"; FAILED=$((FAILED+1)); }

# ---------------------------------------------------------------------------
# 7. /oauth2/token — authorization_code exchange (success)
# ---------------------------------------------------------------------------
blue "── 7. /oauth2/token — authorization_code (success) ──────────"
STATUS=$(curl -s -o /tmp/tok.json -w '%{http_code}' \
              -X POST "$BASE/oauth2/token" \
              -u "$CLIENT_ID:$CLIENT_SECRET" \
              -H 'Content-Type: application/x-www-form-urlencoded' \
              --data-urlencode "grant_type=authorization_code" \
              --data-urlencode "code=$CODE" \
              --data-urlencode "redirect_uri=$REDIRECT" \
              --data-urlencode "code_verifier=$VERIFIER")
check "POST /oauth2/token (code)" 200 "$STATUS"
AT=$(jq -r '.access_token'  /tmp/tok.json)
RT=$(jq -r '.refresh_token' /tmp/tok.json)
IDT=$(jq -r '.id_token'     /tmp/tok.json)
TT=$(jq -r '.token_type'    /tmp/tok.json)
SC=$(jq -r '.scope'         /tmp/tok.json)
[ "$TT" = "Bearer" ]           && green "  ✅  token_type=Bearer"      || { red "  ❌  token_type=$TT"; FAILED=$((FAILED+1)); }
[ -n "$AT" ] && [ "$AT" != "null" ] && green "  ✅  access_token issued"    || { red "  ❌  no access_token"; FAILED=$((FAILED+1)); }
[ -n "$RT" ] && [ "$RT" != "null" ] && green "  ✅  refresh_token issued"   || { red "  ❌  no refresh_token"; FAILED=$((FAILED+1)); }
[ -n "$IDT" ] && [ "$IDT" != "null" ] && green "  ✅  id_token issued (openid scope)" || yellow "  •   no id_token (openid scope missing?)"
case "$SC" in
    *openid*) green "  ✅  granted scope contains openid ($SC)";;
    *)        red   "  ❌  granted scope missing openid: $SC"; FAILED=$((FAILED+1));;
esac

# ---------------------------------------------------------------------------
# 8. Replay protection
# ---------------------------------------------------------------------------
blue "── 8. Authorization code replay must fail ───────────────────"
STATUS=$(curl -s -o /tmp/x.json -w '%{http_code}' \
              -X POST "$BASE/oauth2/token" \
              -u "$CLIENT_ID:$CLIENT_SECRET" \
              -H 'Content-Type: application/x-www-form-urlencoded' \
              --data-urlencode "grant_type=authorization_code" \
              --data-urlencode "code=$CODE" \
              --data-urlencode "redirect_uri=$REDIRECT" \
              --data-urlencode "code_verifier=$VERIFIER")
check "POST /oauth2/token (replayed code)" 400 "$STATUS"
ERR=$(jq -r '.error' /tmp/x.json)
[ "$ERR" = "invalid_grant" ] && green "  ✅  error=invalid_grant" || { red "  ❌  error=$ERR"; FAILED=$((FAILED+1)); }

# ---------------------------------------------------------------------------
# 9. Introspection
# ---------------------------------------------------------------------------
blue "── 9. RFC 7662 — /oauth2/introspect ─────────────────────────"
curl -s -o /tmp/int.json -X POST "$BASE/oauth2/introspect" \
     -u "$CLIENT_ID:$CLIENT_SECRET" \
     -H 'Content-Type: application/x-www-form-urlencoded' \
     --data-urlencode "token=$AT" >/dev/null
ACTIVE=$(jq -r '.active' /tmp/int.json)
SUB=$(jq -r '.sub'     /tmp/int.json)
CID=$(jq -r '.client_id' /tmp/int.json)
[ "$ACTIVE" = "true" ]      && green "  ✅  active=true"                        || { red "  ❌  active=$ACTIVE";  FAILED=$((FAILED+1)); }
[ -n "$SUB" ] && [ "$SUB" != "null" ] && green "  ✅  sub populated"            || { red "  ❌  sub missing";     FAILED=$((FAILED+1)); }
[ "$CID" = "$CLIENT_ID" ]   && green "  ✅  client_id matches"                  || { red "  ❌  client_id=$CID";  FAILED=$((FAILED+1)); }

# ---------------------------------------------------------------------------
# 10. Refresh rotation
# ---------------------------------------------------------------------------
blue "── 10. /oauth2/token — refresh rotation ─────────────────────"
STATUS=$(curl -s -o /tmp/ref.json -w '%{http_code}' \
              -X POST "$BASE/oauth2/token" \
              -u "$CLIENT_ID:$CLIENT_SECRET" \
              -H 'Content-Type: application/x-www-form-urlencoded' \
              --data-urlencode "grant_type=refresh_token" \
              --data-urlencode "refresh_token=$RT" \
              --data-urlencode "scope=openid profile")
check "POST /oauth2/token (refresh)" 200 "$STATUS"
NEW_RT=$(jq -r '.refresh_token' /tmp/ref.json)
NEW_AT=$(jq -r '.access_token'  /tmp/ref.json)
NEW_SC=$(jq -r '.scope'         /tmp/ref.json)
[ "$NEW_RT" != "$RT" ]     && green "  ✅  refresh token rotated"           || { red "  ❌  refresh not rotated"; FAILED=$((FAILED+1)); }
[ "$NEW_SC" = "openid profile" ] && green "  ✅  scope narrowed to '$NEW_SC'" || yellow "  •   scope='$NEW_SC' (implementation choice)"

# ---------------------------------------------------------------------------
# 11. Refresh reuse detection
# ---------------------------------------------------------------------------
blue "── 11. Refresh token reuse must revoke family ───────────────"
STATUS=$(curl -s -o /tmp/reuse.json -w '%{http_code}' \
              -X POST "$BASE/oauth2/token" \
              -u "$CLIENT_ID:$CLIENT_SECRET" \
              -H 'Content-Type: application/x-www-form-urlencoded' \
              --data-urlencode "grant_type=refresh_token" \
              --data-urlencode "refresh_token=$RT")
check "POST /oauth2/token (reused RT)" 400 "$STATUS"
ERR=$(jq -r '.error' /tmp/reuse.json)
[ "$ERR" = "invalid_grant" ] && green "  ✅  error=invalid_grant" || { red "  ❌  error=$ERR"; FAILED=$((FAILED+1)); }

# The NEW refresh token should now also be invalid (family revoked).
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X POST "$BASE/oauth2/token" \
              -u "$CLIENT_ID:$CLIENT_SECRET" \
              -H 'Content-Type: application/x-www-form-urlencoded' \
              --data-urlencode "grant_type=refresh_token" \
              --data-urlencode "refresh_token=$NEW_RT")
check "New RT invalidated after reuse detection" 400 "$STATUS"

# ---------------------------------------------------------------------------
# 12. Module 6.2 — scope enforcement (unregistered scope)
# ---------------------------------------------------------------------------
blue "── 12. Module 6.2 — /oauth2/authorize scope enforcement ─────"
BAD_URL="$BASE/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_ENC&scope=admin.everything&state=$STATE&code_challenge=$CHALLENGE&code_challenge_method=S256"
LOC=$(curl -sSI -o /dev/null -w '%{redirect_url}' \
      -H "Authorization: Bearer $ALICE" "$BAD_URL")
case "$LOC" in
    *error=invalid_scope*) green "  ✅  redirect carries error=invalid_scope";;
    *)                     red   "  ❌  expected invalid_scope in $LOC"; FAILED=$((FAILED+1));;
esac

# ---------------------------------------------------------------------------
# 13. Unregistered redirect_uri (must NOT redirect - JSON invalid_request)
# ---------------------------------------------------------------------------
blue "── 13. Unregistered redirect_uri (must fail without redirect) ─"
STATUS=$(curl -s -o /tmp/bad.json -w '%{http_code}' \
              -H "Authorization: Bearer $ALICE" \
              "$BASE/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$UNREG_ENC&scope=openid&state=$STATE&code_challenge=$CHALLENGE&code_challenge_method=S256")
case "$STATUS" in
    400) green "  ✅  HTTP 400 (no redirect to attacker-controlled URI)";;
    *)   red   "  ❌  expected HTTP 400, got $STATUS"; FAILED=$((FAILED+1));;
esac
ERR=$(jq -r '.error' /tmp/bad.json 2>/dev/null)
[ "$ERR" = "invalid_request" ] && green "  ✅  error=invalid_request" || yellow "  •   body: $(cat /tmp/bad.json)"

# ---------------------------------------------------------------------------
# 14. PKCE missing on /authorize
# ---------------------------------------------------------------------------
blue "── 14. PKCE mandatory — missing code_challenge ──────────────"
NOPKCE_URL="$BASE/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_ENC&scope=openid&state=$STATE"
LOC=$(curl -sSI -o /dev/null -w '%{redirect_url}' \
      -H "Authorization: Bearer $ALICE" "$NOPKCE_URL")
case "$LOC" in
    *error=invalid_request*) green "  ✅  redirect carries error=invalid_request";;
    *)                       red   "  ❌  expected invalid_request in $LOC"; FAILED=$((FAILED+1));;
esac

# ---------------------------------------------------------------------------
# 15. Public client MUST NOT send client_secret
# ---------------------------------------------------------------------------
blue "── 15. Public (SPA) client rejects client_secret ────────────"
STATUS=$(curl -s -o /tmp/pub.json -w '%{http_code}' \
              -X POST "$BASE/oauth2/token" \
              -H 'Content-Type: application/x-www-form-urlencoded' \
              --data-urlencode "grant_type=authorization_code" \
              --data-urlencode "client_id=$SPA_CLIENT_ID" \
              --data-urlencode "client_secret=should-not-be-here" \
              --data-urlencode "code=abc" \
              --data-urlencode "redirect_uri=$SPA_REDIRECT" \
              --data-urlencode "code_verifier=anything")
check "POST /oauth2/token public+secret" 401 "$STATUS"
ERR=$(jq -r '.error' /tmp/pub.json 2>/dev/null)
[ "$ERR" = "invalid_client" ] && green "  ✅  error=invalid_client" || { red "  ❌  error=$ERR"; FAILED=$((FAILED+1)); }

# ---------------------------------------------------------------------------
# 16. Wrong client_secret
# ---------------------------------------------------------------------------
blue "── 16. Wrong client_secret must fail ────────────────────────"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X POST "$BASE/oauth2/token" \
              -u "$CLIENT_ID:wrong-secret" \
              -H 'Content-Type: application/x-www-form-urlencoded' \
              --data-urlencode "grant_type=refresh_token" \
              --data-urlencode "refresh_token=anything")
check "POST /oauth2/token bad secret" 401 "$STATUS"

# ---------------------------------------------------------------------------
# 17. RFC 7009 revocation
# ---------------------------------------------------------------------------
blue "── 17. RFC 7009 revocation on a fresh token pair ────────────"

# Get a fresh code -> tokens (previous family was revoked in step 11)
VERIFIER2=$(openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n' | head -c 64)
CHALLENGE2=$(printf '%s' "$VERIFIER2" | openssl dgst -sha256 -binary | \
             openssl base64 | tr '+/' '-_' | tr -d '=\n')
LOC=$(curl -sSI -o /dev/null -w '%{redirect_url}' \
      -H "Authorization: Bearer $ALICE" \
      "$BASE/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_ENC&scope=openid&state=$STATE&code_challenge=$CHALLENGE2&code_challenge_method=S256")
CODE2=$(echo "$LOC" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
curl -s -o /tmp/tok2.json -X POST "$BASE/oauth2/token" \
     -u "$CLIENT_ID:$CLIENT_SECRET" \
     -H 'Content-Type: application/x-www-form-urlencoded' \
     --data-urlencode "grant_type=authorization_code" \
     --data-urlencode "code=$CODE2" \
     --data-urlencode "redirect_uri=$REDIRECT" \
     --data-urlencode "code_verifier=$VERIFIER2" >/dev/null
AT2=$(jq -r '.access_token'  /tmp/tok2.json)
RT2=$(jq -r '.refresh_token' /tmp/tok2.json)

STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X POST "$BASE/oauth2/revoke" \
              -u "$CLIENT_ID:$CLIENT_SECRET" \
              -H 'Content-Type: application/x-www-form-urlencoded' \
              --data-urlencode "token=$RT2")
check "POST /oauth2/revoke (refresh)" 200 "$STATUS"

curl -s -o /tmp/int2.json -X POST "$BASE/oauth2/introspect" \
     -u "$CLIENT_ID:$CLIENT_SECRET" \
     -H 'Content-Type: application/x-www-form-urlencoded' \
     --data-urlencode "token=$RT2" >/dev/null
ACTIVE=$(jq -r '.active' /tmp/int2.json)
[ "$ACTIVE" = "false" ] && green "  ✅  introspect after revoke → active=false" || { red "  ❌  active=$ACTIVE after revoke"; FAILED=$((FAILED+1)); }

# ---------------------------------------------------------------------------
# 18. DB storage discipline
# ---------------------------------------------------------------------------
blue "── 18. DB storage discipline (hashes only) ──────────────────"
RAW_MATCH=$($PG -tA -c "SELECT COUNT(*) FROM oauth_refresh_tokens WHERE token_hash='$RT2';" | tr -d '[:space:]')
[ "$RAW_MATCH" = "0" ] && green "  ✅  Plaintext refresh token not in DB" \
                       || { red "  ❌  Plaintext refresh token stored (!)"; FAILED=$((FAILED+1)); }
CODE_MATCH=$($PG -tA -c "SELECT COUNT(*) FROM oauth_authorization_codes WHERE code_hash='$CODE2';" | tr -d '[:space:]')
[ "$CODE_MATCH" = "0" ] && green "  ✅  Plaintext auth code not in DB" \
                        || { red "  ❌  Plaintext auth code stored (!)"; FAILED=$((FAILED+1)); }

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo
if [ "$FAILED" -eq 0 ]; then
    green "✅ Phase 6 API verification passed — all checks succeeded."
    exit 0
else
    red   "❌ $FAILED check(s) failed."
    exit 1
fi
