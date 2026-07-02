#!/bin/bash
# ============================================================================
# MeiCrypt Identity Platform - Phase 5 (Client Application Registry)
# end-to-end API verification.
# ----------------------------------------------------------------------------
# What it does:
#   1. Assumes Phase 4 harness has already provisioned users alice + bob and
#      granted alice=admin, bob=member (re-runs the same idempotent DB seed
#      itself so the script also works standalone).
#   2. Exercises the two Phase 5 modules against the running app:
#        5.1  Application Discovery (list / get / update / status / delete)
#        5.2  Credential Issuance   (create, plaintext one-shot, rotate)
#   3. Verifies negative paths:
#        - RBAC forbids member from creating / listing applications
#        - Public client (SPA) never receives a client_secret
#        - Rotating a public client's secret returns 409
#        - REVOKED application cannot transition
#
# Requirements: docker (postgres container "meicrypt-postgres"), curl, jq.
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

require() { command -v "$1" >/dev/null 2>&1 || { red "Missing tool: $1"; exit 2; }; }
require curl
require jq
require docker

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

V7=$($PG -tA -c "SELECT COUNT(*) FROM flyway_schema_history WHERE version='7' AND success=true;" | tr -d '[:space:]')
if [ "$V7" != "1" ]; then
    red "Flyway V7 not applied yet — is the app running with the Phase 5 sources? Aborting."
    exit 1
fi
green "V7 migration applied"

# ---------------------------------------------------------------------------
# 1. Provision alice + bob (idempotent) - mirrors Phase 4 harness
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

# ---------------------------------------------------------------------------
# 2. Ensure memberships + role assignments (same idempotent flow as Phase 4)
# ---------------------------------------------------------------------------
blue "── 2. Ensure memberships + role assignments ─────────────────"
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
# 3. Login
# ---------------------------------------------------------------------------
blue "── 3. Login ─────────────────────────────────────────────────"
login() {
    local email=$1
    local body
    body=$(jq -n --arg o "$ORG" --arg e "$email" --arg p "$PASS" \
                '{organizationId:$o, email:$e, password:$p}')
    curl -s -X POST "$BASE/api/v1/auth/login" \
         -H 'Content-Type: application/json' -d "$body" \
      | jq -r '.accessToken // empty'
}
ALICE=$(login alice@example.com)
BOB=$(login   bob@example.com)
[ -n "$ALICE" ] || { red "Alice login failed"; exit 1; }
[ -n "$BOB"   ] || { red "Bob login failed";   exit 1; }
green "Tokens acquired"

# ---------------------------------------------------------------------------
# 4. Module 5.1 - list (empty or existing)
# ---------------------------------------------------------------------------
blue "── 4. Module 5.1 — list applications (admin) ────────────────"
STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
              "$BASE/api/v1/organizations/$ORG/applications" \
              -H "Authorization: Bearer $ALICE")
check "GET /applications (admin)" 200 "$STATUS"

# ---------------------------------------------------------------------------
# 5. Module 5.2 - create confidential WEB client
# ---------------------------------------------------------------------------
blue "── 5. Module 5.2 — register confidential WEB client ─────────"
SUFFIX=$(date +%s)
CREATE_BODY=$(jq -n --arg name "MeiCrypt CRM $SUFFIX" '{
    name: $name,
    description: "Automated test client (WEB)",
    applicationType: "WEB",
    homepageUrl: "https://crm.example.test",
    redirectUris: ["https://crm.example.test/callback"],
    postLogoutRedirectUris: ["https://crm.example.test/"],
    grantTypes: ["authorization_code","refresh_token"],
    scopes: ["openid","profile","email","crm.read"]
}')
STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/applications" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d "$CREATE_BODY")
check "POST /applications (WEB)" 201 "$STATUS"

APP_ID=$(jq -r '.application.id' /tmp/mip.json)
CLIENT_ID=$(jq -r '.application.clientId' /tmp/mip.json)
CLIENT_SECRET=$(jq -r '.credentials.clientSecret' /tmp/mip.json)
CONF=$(jq -r '.application.confidential' /tmp/mip.json)
PKCE=$(jq -r '.application.requirePkce' /tmp/mip.json)
HAS_SEC=$(jq -r '.application.hasClientSecret' /tmp/mip.json)

case "$CLIENT_ID" in
    mip_*) green "  ✅  client_id prefix ok ($CLIENT_ID)";;
    *)     red   "  ❌  client_id missing mip_ prefix ($CLIENT_ID)"; FAILED=$((FAILED+1));;
esac
case "$CLIENT_SECRET" in
    mips_*) green "  ✅  one-shot client_secret returned (prefix ok)";;
    *)      red   "  ❌  client_secret missing mips_ prefix ($CLIENT_SECRET)"; FAILED=$((FAILED+1));;
esac
[ "$CONF"    = "true" ] && green "  ✅  confidential=true" || { red "  ❌  confidential expected true"; FAILED=$((FAILED+1)); }
[ "$PKCE"    = "true" ] && green "  ✅  requirePkce=true" || { red "  ❌  requirePkce expected true"; FAILED=$((FAILED+1)); }
[ "$HAS_SEC" = "true" ] && green "  ✅  hasClientSecret=true" || { red "  ❌  hasClientSecret expected true"; FAILED=$((FAILED+1)); }

# Persisted hash must NOT equal plaintext
HASH=$($PG -tA -c "SELECT client_secret_hash FROM client_applications WHERE id='$APP_ID';" | tr -d '[:space:]')
if [ -n "$HASH" ] && [ "$HASH" != "$CLIENT_SECRET" ]; then
    green "  ✅  DB stores hashed secret (not plaintext)"
else
    red   "  ❌  DB row does not have a distinct hash!"; FAILED=$((FAILED+1))
fi

# ---------------------------------------------------------------------------
# 6. Get single
# ---------------------------------------------------------------------------
blue "── 6. GET single application ────────────────────────────────"
STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
              "$BASE/api/v1/organizations/$ORG/applications/$APP_ID" \
              -H "Authorization: Bearer $ALICE")
check "GET /applications/{id}" 200 "$STATUS"

# ---------------------------------------------------------------------------
# 7. Module 5.2 - rotate secret
# ---------------------------------------------------------------------------
blue "── 7. Rotate client_secret ──────────────────────────────────"
STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/applications/$APP_ID/rotate-secret" \
              -H "Authorization: Bearer $ALICE")
check "POST /rotate-secret" 200 "$STATUS"
NEW_SECRET=$(jq -r '.clientSecret' /tmp/mip.json)
if [ "$NEW_SECRET" != "$CLIENT_SECRET" ] && [ "${NEW_SECRET#mips_}" != "$NEW_SECRET" ]; then
    green "  ✅  Rotated to a fresh mips_* value"
else
    red   "  ❌  Rotation did not produce a fresh secret"; FAILED=$((FAILED+1))
fi

# ---------------------------------------------------------------------------
# 8. Public client (SPA) — no secret, PKCE forced
# ---------------------------------------------------------------------------
blue "── 8. Register public SPA client ────────────────────────────"
SPA_BODY=$(jq -n --arg name "MeiCrypt SPA $SUFFIX" '{
    name: $name,
    applicationType: "SPA",
    redirectUris: ["http://localhost:5173/callback"],
    scopes: ["openid","profile","email"]
}')
STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/applications" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d "$SPA_BODY")
check "POST /applications (SPA)" 201 "$STATUS"

SPA_ID=$(jq -r '.application.id' /tmp/mip.json)
SPA_SEC=$(jq -r '.credentials.clientSecret' /tmp/mip.json)
SPA_HAS=$(jq -r '.application.hasClientSecret' /tmp/mip.json)
SPA_PKCE=$(jq -r '.application.requirePkce' /tmp/mip.json)

[ "$SPA_SEC"  = "null"  ] && green "  ✅  Public client has null clientSecret" \
                          || { red "  ❌  Public client leaked a secret!"; FAILED=$((FAILED+1)); }
[ "$SPA_HAS"  = "false" ] && green "  ✅  hasClientSecret=false" \
                          || { red "  ❌  hasClientSecret expected false"; FAILED=$((FAILED+1)); }
[ "$SPA_PKCE" = "true"  ] && green "  ✅  PKCE forced on for public client" \
                          || { red "  ❌  requirePkce expected true";  FAILED=$((FAILED+1)); }

# Rotate on public → 409
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/applications/$SPA_ID/rotate-secret" \
              -H "Authorization: Bearer $ALICE")
check "POST /rotate-secret on SPA (must fail)" 409 "$STATUS"

# ---------------------------------------------------------------------------
# 9. Update + status transitions
# ---------------------------------------------------------------------------
blue "── 9. Update + lifecycle transitions ────────────────────────"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X PATCH "$BASE/api/v1/organizations/$ORG/applications/$APP_ID" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d '{"description":"CRM (updated)","scopes":["openid","profile","email","crm.read"]}')
check "PATCH /applications/{id}" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X PATCH "$BASE/api/v1/organizations/$ORG/applications/$APP_ID/status" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d '{"status":"SUSPENDED"}')
check "PATCH /status → SUSPENDED" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X PATCH "$BASE/api/v1/organizations/$ORG/applications/$APP_ID/status" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d '{"status":"ACTIVE"}')
check "PATCH /status → ACTIVE" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X PATCH "$BASE/api/v1/organizations/$ORG/applications/$APP_ID/status" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d '{"status":"REVOKED"}')
check "PATCH /status → REVOKED" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X PATCH "$BASE/api/v1/organizations/$ORG/applications/$APP_ID/status" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d '{"status":"ACTIVE"}')
check "PATCH /status revive REVOKED (must fail)" 409 "$STATUS"

# ---------------------------------------------------------------------------
# 10. RBAC negative cases
# ---------------------------------------------------------------------------
blue "── 10. RBAC — member cannot manage or list ──────────────────"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              "$BASE/api/v1/organizations/$ORG/applications" \
              -H "Authorization: Bearer $BOB")
check "GET /applications as member" 403 "$STATUS"

STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/applications" \
              -H "Authorization: Bearer $BOB" \
              -H 'Content-Type: application/json' \
              -d '{"name":"Nope","applicationType":"WEB"}')
check "POST /applications as member" 403 "$STATUS"

# Unauthenticated
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              "$BASE/api/v1/organizations/$ORG/applications")
case "$STATUS" in
    401|403) green "  ✅  Anonymous denied (HTTP $STATUS)";;
    *)       red   "  ❌  Anonymous should be denied, got $STATUS"; FAILED=$((FAILED+1));;
esac

# ---------------------------------------------------------------------------
# 11. Cleanup - delete SPA
# ---------------------------------------------------------------------------
blue "── 11. Cleanup ──────────────────────────────────────────────"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X DELETE "$BASE/api/v1/organizations/$ORG/applications/$SPA_ID" \
              -H "Authorization: Bearer $ALICE")
check "DELETE /applications/{spa}" 204 "$STATUS"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo
if [ "$FAILED" -eq 0 ]; then
    green "✅ Phase 5 API verification passed — all checks succeeded."
    exit 0
else
    red   "❌ $FAILED check(s) failed."
    exit 1
fi
