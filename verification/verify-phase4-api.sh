#!/bin/bash
# ============================================================================
# MeiCrypt Identity Platform - Phase 4 (RBAC) end-to-end API verification
# ----------------------------------------------------------------------------
# What it does:
#   1. Verifies the app is reachable on :8080.
#   2. Provisions two test users (alice = admin, bob = member) and verifies
#      their emails using the DB-issued tokens.
#   3. Grants alice the SYSTEM "admin" role directly in the DB (bootstrap
#      convenience so she can drive the RBAC APIs).
#   4. Logs both users in and exercises Modules 4.1, 4.2, 4.3, 4.4 with the
#      happy-path AND every negative case the module promises.
#
# Requirements: docker (postgres container "meicrypt-postgres"), curl, jq.
# Safe to re-run: the users, custom role and assignments are idempotent.
# ============================================================================

set -u

BASE=${BASE:-http://localhost:8080}
ORG=${ORG:-00000000-0000-0000-0000-000000000001}
PG="docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity"
PASS='SecurePass123!'

# Log helpers write to stderr so command substitution (`$(...)`) can capture
# pure return values without ANSI noise.
green() { printf '\033[32m%s\033[0m\n' "$*" >&2; }
red()   { printf '\033[31m%s\033[0m\n' "$*" >&2; }
blue()  { printf '\033[34m%s\033[0m\n' "$*" >&2; }
yellow(){ printf '\033[33m%s\033[0m\n' "$*" >&2; }

FAILED=0
STEP=0
check() {  # check "label" expected_status actual_status
    STEP=$((STEP+1))
    local label=$1 expected=$2 actual=$3
    if [ "$expected" = "$actual" ]; then
        green   "  ✅  [$STEP] $label (HTTP $actual)"
    else
        red     "  ❌  [$STEP] $label — expected HTTP $expected, got $actual"
        FAILED=$((FAILED+1))
    fi
}

require() {  # require cmd
    command -v "$1" >/dev/null 2>&1 || { red "Missing required tool: $1"; exit 2; }
}

require curl
require jq
require docker

blue "── 0. Pre-flight ─────────────────────────────────────────────"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health" 2>/dev/null || echo 000)
if [ "$STATUS" != "200" ]; then
    red "App is not reachable on $BASE (health = $STATUS). Start it with 'mvn spring-boot:run' first."
    exit 1
fi
green "App is up on $BASE"

# --------------------------------------------------------------------------
# 1. Provision two users (idempotent)
# --------------------------------------------------------------------------
blue "── 1. Provision test users (alice, bob) ─────────────────────"
register_user() {  # register_user email first last
    local email=$1 first=$2 last=$3
    local body
    body=$(jq -n --arg o "$ORG" --arg e "$email" --arg p "$PASS" \
                --arg f "$first" --arg l "$last" \
                '{organizationId:$o, email:$e, password:$p, firstName:$f, lastName:$l}')
    local resp
    resp=$(curl -s -o /tmp/mip_reg.json -w '%{http_code}' \
                -X POST "$BASE/api/v1/users/register" \
                -H 'Content-Type: application/json' -d "$body")
    if [ "$resp" = "201" ] || [ "$resp" = "200" ]; then
        green "  Registered $email"
    elif [ "$resp" = "409" ]; then
        yellow "  $email already registered (ok)"
    else
        red   "  Unexpected register status $resp for $email"
        cat /tmp/mip_reg.json; echo
    fi
}

register_user alice@example.com Alice Doe
register_user bob@example.com   Bob   Smith

verify_email() {  # verify_email email
    local email=$1
    local user_status
    user_status=$($PG -tA -c "SELECT status FROM users WHERE email='$email';" 2>/dev/null | tr -d '[:space:]')
    if [ "$user_status" = "ACTIVE" ]; then
        yellow "  $email already ACTIVE (ok)"
        return
    fi
    local token
    token=$($PG -tA -c "SELECT token FROM verification_tokens
                        WHERE token_type='EMAIL_VERIFICATION' AND status='PENDING'
                          AND user_id=(SELECT id FROM users WHERE email='$email')
                        ORDER BY created_at DESC LIMIT 1;" | tr -d '[:space:]')
    if [ -z "$token" ]; then
        red "  Could not find pending verification token for $email"
        return
    fi
    curl -s -o /dev/null -X POST "$BASE/api/v1/verification/verify-email" \
        -H 'Content-Type: application/json' \
        -d "{\"token\":\"$token\"}"
    green "  Verified $email"
}

verify_email alice@example.com
verify_email bob@example.com

# --------------------------------------------------------------------------
# 2a. Ensure organization memberships exist (register endpoint does NOT
#     create one automatically at the current Phase 2 impl).
# --------------------------------------------------------------------------
blue "── 2. Ensure org memberships exist for alice & bob ──────────"
ensure_membership() {  # ensure_membership email role  -> stdout: UUID only
    local email=$1 role=$2
    local uid mid
    uid=$($PG -tA -c "SELECT id FROM users WHERE email='$email';" | tr -d '[:space:]')
    mid=$($PG -tA -c "SELECT id FROM organization_memberships
                      WHERE user_id='$uid' AND organization_id='$ORG';" | tr -d '[:space:]')
    if [ -z "$mid" ]; then
        $PG -q -c "INSERT INTO organization_memberships (organization_id, user_id, role, status)
                   VALUES ('$ORG','$uid','$role','ACTIVE');" >/dev/null 2>&1
        mid=$($PG -tA -c "SELECT id FROM organization_memberships
                          WHERE user_id='$uid' AND organization_id='$ORG';" | tr -d '[:space:]')
        green "  Created membership for $email (id=$mid)"
    else
        yellow "  $email already has membership $mid"
    fi
    printf '%s\n' "$mid"    # only the raw UUID on stdout
}

MID_ALICE=$(ensure_membership alice@example.com ADMIN)
MID_BOB=$(ensure_membership   bob@example.com   MEMBER)

ADMIN_RID=$($PG -tA -c "SELECT id FROM roles WHERE organization_id='$ORG' AND slug='admin';" | tr -d '[:space:]')
MEMBER_RID=$($PG -tA -c "SELECT id FROM roles WHERE organization_id='$ORG' AND slug='member';" | tr -d '[:space:]')

if [ -z "$MID_ALICE" ] || [ -z "$ADMIN_RID" ]; then
    red "Missing membership or admin role — is Flyway V5/V6 applied? Aborting."
    exit 1
fi

# --------------------------------------------------------------------------
# 2b. Grant Alice the SYSTEM admin role, Bob the SYSTEM member role.
# --------------------------------------------------------------------------
blue "── 3. Grant Alice=admin, Bob=member ─────────────────────────"
$PG -q -c "INSERT INTO membership_role_assignments (membership_id, role_id)
           VALUES ('$MID_ALICE','$ADMIN_RID')
           ON CONFLICT DO NOTHING;" >/dev/null
$PG -q -c "INSERT INTO membership_role_assignments (membership_id, role_id)
           VALUES ('$MID_BOB','$MEMBER_RID')
           ON CONFLICT DO NOTHING;" >/dev/null
green "Alice → admin (membership=$MID_ALICE)"
green "Bob   → member (membership=$MID_BOB)"

# --------------------------------------------------------------------------
# 4. Login both users
# --------------------------------------------------------------------------
blue "── 4. Login ─────────────────────────────────────────────────"
login() {  # login email  -> stdout: raw access token
    local email=$1
    local body
    body=$(jq -n --arg o "$ORG" --arg e "$email" --arg p "$PASS" \
                '{organizationId:$o, email:$e, password:$p}')
    local tok
    tok=$(curl -s -X POST "$BASE/api/v1/auth/login" \
                -H 'Content-Type: application/json' \
                -d "$body" | jq -r '.accessToken // empty')
    if [ -z "$tok" ]; then
        red "  Login failed for $email"; return 1
    fi
    printf '%s\n' "$tok"
}

ALICE=$(login alice@example.com) || exit 1
BOB=$(login bob@example.com)     || exit 1
green "Alice token: ${ALICE:0:40}..."
green "Bob   token: ${BOB:0:40}..."

# --------------------------------------------------------------------------
# 5. Module 4.1 — Permission catalog
# --------------------------------------------------------------------------
blue "── 5. Module 4.1 — Permission catalog ───────────────────────"

STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
              "$BASE/api/v1/rbac/permissions" \
              -H "Authorization: Bearer $ALICE")
check "GET /rbac/permissions (authenticated)" 200 "$STATUS"
COUNT=$(jq 'length' /tmp/mip.json)
if [ "$COUNT" -ge "23" ]; then
    green "  ✅  Catalog contains $COUNT permissions"
else
    red   "  ❌  Catalog only contains $COUNT permissions (expected ≥ 23)"; FAILED=$((FAILED+1))
fi

STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
              "$BASE/api/v1/rbac/permissions?domain=rbac" \
              -H "Authorization: Bearer $ALICE")
check "GET /rbac/permissions?domain=rbac" 200 "$STATUS"
jq -r '.[] | .code' /tmp/mip.json | sed 's/^/       /'

STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/rbac/permissions")
check "GET /rbac/permissions (anonymous → denied)" 403 "$STATUS"

# --------------------------------------------------------------------------
# 6. Module 4.2 — Role CRUD
# --------------------------------------------------------------------------
blue "── 6. Module 4.2 — Role CRUD ────────────────────────────────"

STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
              "$BASE/api/v1/organizations/$ORG/roles" \
              -H "Authorization: Bearer $ALICE")
check "GET /organizations/{org}/roles (alice)" 200 "$STATUS"
jq -r '.[] | "       - \(.slug) [\(.type)] default=\(.defaultRole)"' /tmp/mip.json

# Create (or reuse) a CUSTOM role
BODY=$(jq -n '{name:"Session Auditor",
               description:"Read active sessions and audit logs",
               permissionCodes:["identity:session:read","audit:log:read"]}')
STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/roles" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d "$BODY")
if [ "$STATUS" = "201" ]; then
    green "  ✅  Created new CUSTOM role"
    RID=$(jq -r '.id' /tmp/mip.json)
elif [ "$STATUS" = "409" ]; then
    yellow "  ⚠️  Custom role already exists (re-using)"
    RID=$(curl -s "$BASE/api/v1/organizations/$ORG/roles" \
              -H "Authorization: Bearer $ALICE" \
          | jq -r '.[] | select(.slug=="session-auditor") | .id')
else
    red   "  ❌  Unexpected status $STATUS creating role"; FAILED=$((FAILED+1))
    cat /tmp/mip.json; echo
    RID=""
fi
echo "       roleId=$RID"

# PATCH update
if [ -n "$RID" ]; then
    STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
                  -X PATCH "$BASE/api/v1/organizations/$ORG/roles/$RID" \
                  -H "Authorization: Bearer $ALICE" \
                  -H 'Content-Type: application/json' \
                  -d '{"description":"Read sessions, devices and audit logs",
                       "permissionCodes":["identity:session:read","identity:session:revoke","audit:log:read"]}')
    check "PATCH /roles/{id}" 200 "$STATUS"
fi

# SYSTEM role immutability
SYS_RID=$(curl -s "$BASE/api/v1/organizations/$ORG/roles" \
              -H "Authorization: Bearer $ALICE" \
          | jq -r '.[] | select(.slug=="admin") | .id')
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X DELETE "$BASE/api/v1/organizations/$ORG/roles/$SYS_RID" \
              -H "Authorization: Bearer $ALICE")
check "DELETE SYSTEM role (should be blocked)" 409 "$STATUS"

# Unknown permission code
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/roles" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d '{"name":"Broken","permissionCodes":["bogus:permission:code"]}')
check "POST role with unknown permission" 400 "$STATUS"

# --------------------------------------------------------------------------
# 7. Module 4.3 — Role assignments
# --------------------------------------------------------------------------
blue "── 7. Module 4.3 — Role assignments ─────────────────────────"

STATUS=$(curl -s -o /tmp/mip.json -w '%{http_code}' \
              "$BASE/api/v1/organizations/$ORG/memberships/$MID_ALICE/role-assignments" \
              -H "Authorization: Bearer $ALICE")
check "GET alice's role assignments" 200 "$STATUS"
jq -r '.[] | "       - \(.roleSlug) [\(.roleType)]"' /tmp/mip.json 2>/dev/null || jq '.' /tmp/mip.json

if [ -n "$RID" ]; then
    # Assign custom role to Bob
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
                  -X POST "$BASE/api/v1/organizations/$ORG/memberships/$MID_BOB/role-assignments" \
                  -H "Authorization: Bearer $ALICE" \
                  -H 'Content-Type: application/json' \
                  -d "{\"roleId\":\"$RID\"}")
    if [ "$STATUS" = "201" ] || [ "$STATUS" = "409" ]; then
        green "  ✅  Assigned CUSTOM role to Bob (HTTP $STATUS)"
    else
        red   "  ❌  Assign failed with HTTP $STATUS"; FAILED=$((FAILED+1))
    fi

    # Duplicate assign
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
                  -X POST "$BASE/api/v1/organizations/$ORG/memberships/$MID_BOB/role-assignments" \
                  -H "Authorization: Bearer $ALICE" \
                  -H 'Content-Type: application/json' \
                  -d "{\"roleId\":\"$RID\"}")
    check "Duplicate assignment (should conflict)" 409 "$STATUS"

    # Revoke
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
                  -X DELETE "$BASE/api/v1/organizations/$ORG/memberships/$MID_BOB/role-assignments/$RID" \
                  -H "Authorization: Bearer $ALICE")
    check "Revoke role from Bob" 204 "$STATUS"
fi

# Cross-tenant assignment attempt (only runnable if a 2nd org exists)
OTHER_ROLE=$($PG -tA -c "SELECT r.id FROM roles r
                          JOIN organizations o ON o.id = r.organization_id
                         WHERE o.id <> '$ORG' LIMIT 1;" | tr -d '[:space:]')
if [ -n "$OTHER_ROLE" ]; then
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
                  -X POST "$BASE/api/v1/organizations/$ORG/memberships/$MID_ALICE/role-assignments" \
                  -H "Authorization: Bearer $ALICE" \
                  -H 'Content-Type: application/json' \
                  -d "{\"roleId\":\"$OTHER_ROLE\"}")
    check "Cross-tenant role assignment (should be forbidden)" 403 "$STATUS"
else
    yellow "  ⚠️  Skipping cross-tenant test (only one organization in DB)"
fi

# --------------------------------------------------------------------------
# 8. Module 4.4 — @PreAuthorize evaluation
# --------------------------------------------------------------------------
blue "── 8. Module 4.4 — @PreAuthorize evaluation ─────────────────"

# Bob (member only) cannot create roles
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/roles" \
              -H "Authorization: Bearer $BOB" \
              -H 'Content-Type: application/json' \
              -d '{"name":"Nope","permissionCodes":["identity:user:read"]}')
check "Bob (member) creates role — must be forbidden" 403 "$STATUS"

# Bob CAN list roles (member has rbac:role:read)
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              "$BASE/api/v1/organizations/$ORG/roles" \
              -H "Authorization: Bearer $BOB")
check "Bob (member) lists roles — allowed" 200 "$STATUS"

# Cross-tenant read (only if a 2nd org exists)
OTHER_ORG=$($PG -tA -c "SELECT id FROM organizations WHERE id <> '$ORG' LIMIT 1;" | tr -d '[:space:]')
if [ -n "$OTHER_ORG" ]; then
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
                  "$BASE/api/v1/organizations/$OTHER_ORG/roles" \
                  -H "Authorization: Bearer $BOB")
    check "Bob reads a different org's roles — must be forbidden" 403 "$STATUS"
else
    yellow "  ⚠️  Skipping cross-tenant read test (only one organization)"
fi

# Print Alice's effective authorities for reference
blue "── 9. Effective authorities (from DB) ───────────────────────"
$PG -c "SELECT p.code
         FROM users u
         JOIN organization_memberships m ON m.user_id = u.id
         JOIN membership_role_assignments mra ON mra.membership_id = m.id
         JOIN role_permissions rp ON rp.role_id = mra.role_id
         JOIN permissions p ON p.id = rp.permission_id
        WHERE u.email='alice@example.com' AND m.organization_id='$ORG'
        ORDER BY p.code;"

echo
if [ "$FAILED" -eq 0 ]; then
    green "═════════════════════════════════════════════════════════════"
    green " ✅  Phase 4 API verification passed — all checks succeeded."
    green "═════════════════════════════════════════════════════════════"
    exit 0
else
    red   "═════════════════════════════════════════════════════════════"
    red   " ❌  Phase 4 API verification: $FAILED check(s) failed."
    red   "═════════════════════════════════════════════════════════════"
    exit 1
fi
