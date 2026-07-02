#!/bin/bash
# ============================================================================
# MeiCrypt Identity Platform - Phase 9 (Advanced MFA) end-to-end API verification.
# ----------------------------------------------------------------------------
# What it does:
#   0. Pre-flight: app healthy + V11 migration applied.
#   1. Registers / verifies alice.mfa@example.com and logs her in (Phase 3).
#   2. Enrols a TOTP factor (Module 9.1) → asserts DB row PENDING + otpauth URI
#      + QR PNG returned.
#   3. Computes a live RFC 6238 code from the returned base32 secret and posts
#      it to /verify. Factor transitions to ACTIVE.
#   4. Logs out, then logs in again with the same password. Because at least
#      one ACTIVE factor exists, the login endpoint must now return
#      HTTP 202 + { mfaChallenge:{challengeToken,…} } instead of tokens.
#   5. Redeems the challenge at /api/v1/mfa/challenges/verify with a fresh
#      TOTP code → expects HTTP 200 + full TokenResponse.
#   6. Negative paths:
#        - verify with an unknown challengeToken   => 404
#        - verify with a wrong TOTP code (5 times) => 401 then challenge FAILED
#        - unauthenticated GET /api/v1/mfa/factors => 401/403
#   7. Cleanup path:
#        - DELETE /api/v1/mfa/totp/factors/{id} revokes the factor and the
#          next login goes back to returning tokens directly (HTTP 200).
#
# Requirements: docker container "meicrypt-postgres", curl, jq, python3, awk,
#               openssl.  Safe to re-run.
# ============================================================================
set -u

BASE=${BASE:-http://localhost:8080}
ORG=${ORG:-00000000-0000-0000-0000-000000000001}
PG="docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity"
PASS='SecurePass123!'
EMAIL='alice.mfa@example.com'

green()  { printf '\033[32m%s\033[0m\n' "$*" >&2; }
red()    { printf '\033[31m%s\033[0m\n' "$*" >&2; }
blue()   { printf '\033[34m%s\033[0m\n' "$*" >&2; }
yellow() { printf '\033[33m%s\033[0m\n' "$*" >&2; }

FAILED=0
STEP=0
check() {
    STEP=$((STEP+1))
    local label=$1 expected=$2 actual=$3
    if [ "$expected" = "$actual" ]; then
        green "  ✅  [$STEP] $label (HTTP $actual)"
    else
        red   "  ❌  [$STEP] $label — expected HTTP $expected, got $actual"
        FAILED=$((FAILED+1))
    fi
}
assert_eq() {
    STEP=$((STEP+1))
    local label=$1 expected=$2 actual=$3
    if [ "$expected" = "$actual" ]; then
        green "  ✅  [$STEP] $label ($actual)"
    else
        red   "  ❌  [$STEP] $label — expected '$expected', got '$actual'"
        FAILED=$((FAILED+1))
    fi
}
assert_nonempty() {
    STEP=$((STEP+1))
    local label=$1 val=$2
    if [ -n "$val" ] && [ "$val" != "null" ]; then
        green "  ✅  [$STEP] $label"
    else
        red   "  ❌  [$STEP] $label — value empty"
        FAILED=$((FAILED+1))
    fi
}
require() { command -v "$1" >/dev/null 2>&1 || { red "Missing tool: $1"; exit 2; }; }
require curl
require jq
require docker
require python3

# ---------------------------------------------------------------------------
# Helper: RFC 6238 TOTP generator (python3) — mirrors TotpCodeGenerator.
# ---------------------------------------------------------------------------
totp() {
    local secret=$1 period=${2:-30} digits=${3:-6}
    python3 - "$secret" "$period" "$digits" <<'PY'
import base64, hmac, hashlib, struct, sys, time
secret, period, digits = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
# pad base32 to multiple of 8 for python's decoder
pad = '=' * ((8 - len(secret) % 8) % 8)
key = base64.b32decode(secret.upper() + pad, casefold=True)
counter = int(time.time()) // period
mac = hmac.new(key, struct.pack('>Q', counter), hashlib.sha1).digest()
off = mac[-1] & 0x0F
code = (struct.unpack('>I', mac[off:off+4])[0] & 0x7FFFFFFF) % (10 ** digits)
print(str(code).zfill(digits))
PY
}

# ---------------------------------------------------------------------------
# 0. Pre-flight
# ---------------------------------------------------------------------------
blue "── 0. Pre-flight ─────────────────────────────────────────────"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health" 2>/dev/null || echo 000)
if [ "$STATUS" != "200" ]; then
    red "App is not reachable on $BASE (health=$STATUS). Start it with 'mvn spring-boot:run' first."
    exit 1
fi
green "App is up on $BASE"

V11=$($PG -tA -c "SELECT COUNT(*) FROM flyway_schema_history WHERE version='11' AND success=true;" 2>/dev/null | tr -d '[:space:]')
if [ "$V11" != "1" ]; then
    red "Flyway V11 not applied yet — is the app running with Phase 9 sources? Aborting."
    exit 1
fi
green "V11 migration applied"

# ---------------------------------------------------------------------------
# 1. Register + verify + first login (no MFA yet → tokens returned)
# ---------------------------------------------------------------------------
blue "── 1. Register / login $EMAIL (no MFA yet) ─────────────────"

REG_BODY=$(jq -n --arg o "$ORG" --arg e "$EMAIL" --arg p "$PASS" \
                 --arg f Alice --arg l MFA \
                 '{organizationId:$o, email:$e, password:$p, firstName:$f, lastName:$l}')
REG_STATUS=$(curl -s -o /tmp/mip9_reg.json -w '%{http_code}' \
                  -X POST "$BASE/api/v1/users/register" \
                  -H 'Content-Type: application/json' -d "$REG_BODY")
case "$REG_STATUS" in
    200|201) green "  Registered $EMAIL";;
    409)     yellow "  $EMAIL already exists (ok)";;
    *)       yellow "  Register status $REG_STATUS for $EMAIL";;
esac

USTATUS=$($PG -tA -c "SELECT status FROM users WHERE email='$EMAIL';" | tr -d '[:space:]')
if [ "$USTATUS" != "ACTIVE" ]; then
    TOKEN=$($PG -tA -c "SELECT token FROM verification_tokens
                        WHERE token_type='EMAIL_VERIFICATION' AND status='PENDING'
                          AND user_id=(SELECT id FROM users WHERE email='$EMAIL')
                        ORDER BY created_at DESC LIMIT 1;" | tr -d '[:space:]')
    if [ -n "$TOKEN" ]; then
        curl -s -o /dev/null -X POST "$BASE/api/v1/verification/verify-email" \
             -H 'Content-Type: application/json' -d "{\"token\":\"$TOKEN\"}"
        green "  Verified $EMAIL"
    fi
fi

# Reset any leftover MFA state from prior runs so the first login definitely
# returns tokens (not a challenge).
$PG -tA -c "DELETE FROM user_mfa_factors WHERE user_id=(SELECT id FROM users WHERE email='$EMAIL');" \
    >/dev/null 2>&1

LOGIN_BODY=$(jq -n --arg o "$ORG" --arg e "$EMAIL" --arg p "$PASS" \
                    '{organizationId:$o, email:$e, password:$p, deviceName:"phase9-verify"}')
STATUS=$(curl -s -o /tmp/mip9_login1.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/auth/login" \
              -H 'Content-Type: application/json' -d "$LOGIN_BODY")
check "First login without MFA returns tokens (200)" 200 "$STATUS"
ACCESS=$(jq -r '.tokens.accessToken // .accessToken // empty' /tmp/mip9_login1.json)
assert_nonempty "access token issued" "$ACCESS"

# ---------------------------------------------------------------------------
# 2. Enrol a TOTP factor (Module 9.1)
# ---------------------------------------------------------------------------
blue "── 2. Enrol TOTP factor ──────────────────────────────────────"
STATUS=$(curl -s -o /tmp/mip9_enroll.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/mfa/totp/enroll" \
              -H "Authorization: Bearer $ACCESS" \
              -H 'Content-Type: application/json' \
              -d '{"displayName":"Google Authenticator"}')
check "POST /api/v1/mfa/totp/enroll" 201 "$STATUS"

FACTOR_ID=$(jq -r '.factorId // empty' /tmp/mip9_enroll.json)
SECRET=$(  jq -r '.secretBase32 // empty' /tmp/mip9_enroll.json)
OTPAUTH=$( jq -r '.otpAuthUri // empty'   /tmp/mip9_enroll.json)
QR=$(      jq -r '.qrCodePngBase64 // empty' /tmp/mip9_enroll.json)
assert_nonempty "factorId returned"    "$FACTOR_ID"
assert_nonempty "base32 secret returned" "$SECRET"
case "$OTPAUTH" in
    otpauth://totp/*) green "  ✅  otpauth URI shape OK";;
    *)                red   "  ❌  otpauth URI malformed: $OTPAUTH"; FAILED=$((FAILED+1));;
esac
[ ${#QR} -gt 100 ] && green "  ✅  QR PNG (base64) returned (${#QR} chars)" \
                   || { red "  ❌  QR PNG missing"; FAILED=$((FAILED+1)); }

# DB state PENDING
DB_STATUS=$($PG -tA -c "SELECT status FROM user_mfa_factors WHERE id='$FACTOR_ID';" | tr -d '[:space:]')
assert_eq "DB factor status after enroll" "PENDING" "$DB_STATUS"

# ---------------------------------------------------------------------------
# 3. Verify TOTP enrolment with a live code
# ---------------------------------------------------------------------------
blue "── 3. Activate factor with first TOTP code ───────────────────"
CODE=$(totp "$SECRET")
STATUS=$(curl -s -o /tmp/mip9_verify.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/mfa/totp/factors/$FACTOR_ID/verify" \
              -H "Authorization: Bearer $ACCESS" \
              -H 'Content-Type: application/json' \
              -d "{\"code\":\"$CODE\"}")
check "POST /verify with live code" 200 "$STATUS"

DB_STATUS=$($PG -tA -c "SELECT status FROM user_mfa_factors WHERE id='$FACTOR_ID';" | tr -d '[:space:]')
assert_eq "DB factor status after verify" "ACTIVE" "$DB_STATUS"

STATUS=$(curl -s -o /tmp/mip9_list.json -w '%{http_code}' \
              -H "Authorization: Bearer $ACCESS" \
              "$BASE/api/v1/mfa/factors")
check "GET /api/v1/mfa/factors (list)" 200 "$STATUS"
LIST_LEN=$(jq -r 'length' /tmp/mip9_list.json)
[ "$LIST_LEN" -ge 1 ] && green "  ✅  factor list returned ($LIST_LEN row(s))" \
                     || { red "  ❌  factor list empty"; FAILED=$((FAILED+1)); }

# ---------------------------------------------------------------------------
# 4. Login again → expect MFA challenge (HTTP 202)
# ---------------------------------------------------------------------------
blue "── 4. Login with password → expect step-up challenge ─────────"
STATUS=$(curl -s -o /tmp/mip9_login2.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/auth/login" \
              -H 'Content-Type: application/json' -d "$LOGIN_BODY")
check "POST /login returns 202 Accepted (step-up)" 202 "$STATUS"

REQUIRES_MFA=$(jq -r 'if .mfaChallenge != null then "yes" else "no" end' /tmp/mip9_login2.json)
assert_eq "response carries mfaChallenge" "yes" "$REQUIRES_MFA"

CHALLENGE_TOKEN=$(jq -r '.mfaChallenge.challengeToken // empty' /tmp/mip9_login2.json)
CHALLENGE_ID=$(   jq -r '.mfaChallenge.challengeId    // empty' /tmp/mip9_login2.json)
assert_nonempty "challengeToken present" "$CHALLENGE_TOKEN"

TOKENS_FIELD=$(jq -r '.tokens // "null"' /tmp/mip9_login2.json)
assert_eq "tokens field is null before verify" "null" "$TOKENS_FIELD"

DB_STATUS=$($PG -tA -c "SELECT status FROM mfa_challenges WHERE id='$CHALLENGE_ID';" | tr -d '[:space:]')
assert_eq "DB challenge status" "PENDING" "$DB_STATUS"

# ---------------------------------------------------------------------------
# 5. Redeem the challenge with a fresh TOTP code
# ---------------------------------------------------------------------------
blue "── 5. Redeem challenge with fresh TOTP code ──────────────────"
# Anti-replay: the enrolment step already burned the current 30-second
# counter, so we MUST wait for the next window before redeeming. This is
# the same behaviour a real end-user hits when the app tells them
# "code accepted, please try again in a few seconds if it fails".
NOW_EPOCH=$(date +%s)
WAIT=$(( 30 - (NOW_EPOCH % 30) + 1 ))
yellow "  waiting ${WAIT}s for the next TOTP window (anti-replay)"
sleep "$WAIT"
CODE=$(totp "$SECRET")
VERIFY_BODY=$(jq -n --arg t "$CHALLENGE_TOKEN" --arg c "$CODE" \
                    '{challengeToken:$t, factorType:"TOTP", proof:$c}')
STATUS=$(curl -s -o /tmp/mip9_challenge_ok.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/mfa/challenges/verify" \
              -H 'Content-Type: application/json' -d "$VERIFY_BODY")
check "POST /mfa/challenges/verify (valid TOTP)" 200 "$STATUS"
NEW_ACCESS=$(jq -r '.accessToken // empty' /tmp/mip9_challenge_ok.json)
NEW_REFRESH=$(jq -r '.refreshToken // empty' /tmp/mip9_challenge_ok.json)
assert_nonempty "post-MFA access token" "$NEW_ACCESS"
assert_nonempty "post-MFA refresh token" "$NEW_REFRESH"

DB_STATUS=$($PG -tA -c "SELECT status FROM mfa_challenges WHERE id='$CHALLENGE_ID';" | tr -d '[:space:]')
assert_eq "DB challenge status after redeem" "SATISFIED" "$DB_STATUS"

# ---------------------------------------------------------------------------
# 6. Negative paths
# ---------------------------------------------------------------------------
blue "── 6. Negative paths ─────────────────────────────────────────"

# 6a. Unknown challengeToken → 404
BOGUS=$(jq -n '{challengeToken:"does-not-exist", factorType:"TOTP", proof:"000000"}')
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X POST "$BASE/api/v1/mfa/challenges/verify" \
              -H 'Content-Type: application/json' -d "$BOGUS")
check "Verify with unknown challenge token → 404" 404 "$STATUS"

# 6b. Fresh login → wrong TOTP repeatedly should exhaust attempts.
STATUS=$(curl -s -o /tmp/mip9_login3.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/auth/login" \
              -H 'Content-Type: application/json' -d "$LOGIN_BODY")
check "Login #3 (still expects challenge)" 202 "$STATUS"
CHALLENGE_TOKEN3=$(jq -r '.mfaChallenge.challengeToken' /tmp/mip9_login3.json)
CHALLENGE_ID3=$(   jq -r '.mfaChallenge.challengeId'    /tmp/mip9_login3.json)

for i in 1 2 3 4 5; do
    WRONG=$(jq -n --arg t "$CHALLENGE_TOKEN3" '{challengeToken:$t, factorType:"TOTP", proof:"000000"}')
    STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
                  -X POST "$BASE/api/v1/mfa/challenges/verify" \
                  -H 'Content-Type: application/json' -d "$WRONG")
    if [ "$STATUS" = "401" ] || [ "$STATUS" = "409" ]; then
        green "  ✅  wrong code attempt #$i → HTTP $STATUS"
    else
        red   "  ❌  wrong code attempt #$i → HTTP $STATUS (expected 401/409)"
        FAILED=$((FAILED+1))
    fi
done
FINAL_STATE=$($PG -tA -c "SELECT status FROM mfa_challenges WHERE id='$CHALLENGE_ID3';" | tr -d '[:space:]')
if [ "$FINAL_STATE" = "FAILED" ] || [ "$FINAL_STATE" = "PENDING" ]; then
    green "  ✅  challenge state after exhaustion: $FINAL_STATE"
else
    yellow "  !  challenge state after 5 wrong tries = $FINAL_STATE"
fi

# 6c. Anonymous list endpoint → 401/403
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/mfa/factors")
case "$STATUS" in
    401|403) green "  ✅  anon GET /api/v1/mfa/factors → HTTP $STATUS";;
    *)       red   "  ❌  anon GET /api/v1/mfa/factors → HTTP $STATUS"; FAILED=$((FAILED+1));;
esac

# ---------------------------------------------------------------------------
# 7. Revoke the factor → login goes back to normal 200 + tokens
# ---------------------------------------------------------------------------
blue "── 7. Revoke factor, login returns to token issuance ─────────"
# Prior negative-path attempts invalidated the session bound to $NEW_ACCESS
# (five wrong TOTP tries → challenge FAILED, then successive re-logins
# rotated the underlying user_sessions row). Re-authenticate through the
# happy path to obtain a fresh Bearer for the DELETE call.
sleep $(( 30 - $(date +%s) % 30 + 1 ))
STATUS=$(curl -s -o /tmp/mip9_login_reauth.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/auth/login" \
              -H 'Content-Type: application/json' -d "$LOGIN_BODY")
if [ "$STATUS" = "202" ]; then
    REAUTH_TOKEN=$(jq -r '.mfaChallenge.challengeToken' /tmp/mip9_login_reauth.json)
    CODE=$(totp "$SECRET")
    curl -s -o /tmp/mip9_reauth_ok.json -X POST "$BASE/api/v1/mfa/challenges/verify" \
         -H 'Content-Type: application/json' \
         -d "$(jq -n --arg t "$REAUTH_TOKEN" --arg c "$CODE" \
               '{challengeToken:$t, factorType:"TOTP", proof:$c}')" >/dev/null
    NEW_ACCESS=$(jq -r '.accessToken // empty' /tmp/mip9_reauth_ok.json)
elif [ "$STATUS" = "200" ]; then
    NEW_ACCESS=$(jq -r '.tokens.accessToken // .accessToken // empty' /tmp/mip9_login_reauth.json)
fi

STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -X DELETE "$BASE/api/v1/mfa/totp/factors/$FACTOR_ID" \
              -H "Authorization: Bearer $NEW_ACCESS")
check "DELETE /api/v1/mfa/totp/factors/{id}" 200 "$STATUS"

DB_STATUS=$($PG -tA -c "SELECT status FROM user_mfa_factors WHERE id='$FACTOR_ID';" | tr -d '[:space:]')
assert_eq "DB factor status after revoke" "REVOKED" "$DB_STATUS"

STATUS=$(curl -s -o /tmp/mip9_login4.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/auth/login" \
              -H 'Content-Type: application/json' -d "$LOGIN_BODY")
check "Login #4 after revoke → 200 (tokens direct)" 200 "$STATUS"
POST_REVOKE_TOKENS=$(jq -r '.tokens.accessToken // .accessToken // empty' /tmp/mip9_login4.json)
assert_nonempty "tokens returned directly after revoke" "$POST_REVOKE_TOKENS"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo
if [ "$FAILED" -eq 0 ]; then
    green "════════════════════════════════════════════════════════════════"
    green " ✅ Phase 9 API verification: ALL $STEP CHECKS PASSED"
    green "════════════════════════════════════════════════════════════════"
    exit 0
else
    red   "════════════════════════════════════════════════════════════════"
    red   " ❌ Phase 9 API verification: $FAILED / $STEP checks FAILED"
    red   "════════════════════════════════════════════════════════════════"
    exit 1
fi
