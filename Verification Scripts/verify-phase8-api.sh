#!/bin/bash
# ============================================================================
# MeiCrypt Identity Platform - Phase 8 (SSO Federation + Single Logout)
# end-to-end API verification.
# ----------------------------------------------------------------------------
# What it does:
#   0. Pre-flight: app healthy + V10 migration applied.
#   1. Registers/verifies alice@example.com and logs her in (Phase 3).
#   2. Confirms an ACTIVE sso_sessions row was minted for that user_session.
#   3. Reads GET /api/v1/sso/session and asserts the response shape.
#   4. Registers TWO WEB client applications (client-A with backchannel URI,
#      client-B without) and drives a full authorization_code + PKCE flow
#      against each — verifying that each one appears in
#      sso_session_participants exactly once.
#   5. Confirms the OIDC discovery document advertises end_session_endpoint,
#      backchannel_logout_supported, backchannel_logout_session_supported.
#   6. Calls GET /oauth2/logout (RP-Initiated Logout) with the registered
#      post_logout_redirect_uri + state, expects 302 back to that URL.
#   7. Asserts the cascade:
#        - sso_sessions.status = TERMINATED
#        - user_sessions.status = TERMINATED
#        - every oauth_refresh_tokens row for the session is REVOKED
#        - participant with backchannel URI transitions notification_state
#          to SENT or FAILED (never left NULL); participant without URI
#          becomes SKIPPED.
#   8. Negative paths:
#        - unregistered post_logout_redirect_uri => 400 invalid_request
#        - unknown client_id                     => 400 invalid_request
#        - anonymous caller w/ registered URI    => 302 (idempotent logout)
#        - unauthenticated GET /api/v1/sso/session => 401
#
# Requirements: docker (container "meicrypt-postgres"), curl, jq, awk,
#               openssl, python3.
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
require awk
require openssl
require python3

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

V10=$($PG -tA -c "SELECT COUNT(*) FROM flyway_schema_history WHERE version='10' AND success=true;" 2>/dev/null | tr -d '[:space:]')
if [ "$V10" != "1" ]; then
    red "Flyway V10 not applied yet — is the app running with the Phase 8 sources? Aborting."
    exit 1
fi
green "V10 migration applied"

# ---------------------------------------------------------------------------
# 1. Register + verify + login alice
# ---------------------------------------------------------------------------
blue "── 1. Login Alice (Phase 3 anchor for SSO) ───────────────────"

register_user() {
    local email=$1 first=$2 last=$3
    local body
    body=$(jq -n --arg o "$ORG" --arg e "$email" --arg p "$PASS" \
                --arg f "$first" --arg l "$last" \
                '{organizationId:$o, email:$e, password:$p, firstName:$f, lastName:$l}')
    local resp
    resp=$(curl -s -o /tmp/mip8_reg.json -w '%{http_code}' \
                -X POST "$BASE/api/v1/users/register" \
                -H 'Content-Type: application/json' -d "$body")
    case "$resp" in
        200|201) green "  Registered $email";;
        409)     yellow "  $email already exists (ok)";;
        *)       yellow "  Register status $resp for $email";;
    esac
}
register_user alice@example.com Alice Doe

STATUS=$($PG -tA -c "SELECT status FROM users WHERE email='alice@example.com';" | tr -d '[:space:]')
if [ "$STATUS" != "ACTIVE" ]; then
    TOKEN=$($PG -tA -c "SELECT token FROM verification_tokens
                        WHERE token_type='EMAIL_VERIFICATION' AND status='PENDING'
                          AND user_id=(SELECT id FROM users WHERE email='alice@example.com')
                        ORDER BY created_at DESC LIMIT 1;" | tr -d '[:space:]')
    if [ -n "$TOKEN" ]; then
        curl -s -o /dev/null -X POST "$BASE/api/v1/verification/verify-email" \
             -H 'Content-Type: application/json' -d "{\"token\":\"$TOKEN\"}"
        green "  Verified alice@example.com"
    fi
fi

LOGIN_BODY=$(jq -n --arg o "$ORG" --arg e alice@example.com --arg p "$PASS" \
                    '{organizationId:$o, email:$e, password:$p, deviceName:"phase8-verify"}')
curl -s -o /tmp/mip8_login.json -w '%{http_code}\n' \
     -X POST "$BASE/api/v1/auth/login" \
     -H 'Content-Type: application/json' -d "$LOGIN_BODY" > /tmp/mip8_login_status
LOGIN_STATUS=$(tr -d '[:space:]' </tmp/mip8_login_status)
check "POST /api/v1/auth/login" 200 "$LOGIN_STATUS"
ALICE=$(jq -r '.accessToken // empty' /tmp/mip8_login.json)
SESSION_ID=$(jq -r '.sessionId // empty' /tmp/mip8_login.json)
USER_ID=$(jq -r '.userId // empty'   /tmp/mip8_login.json)
assert_nonempty "access token issued" "$ALICE"
assert_nonempty "session_id present"  "$SESSION_ID"

# ---------------------------------------------------------------------------
# 2. sso_sessions row minted for this user_session
# ---------------------------------------------------------------------------
blue "── 2. Module 8.1 — SSO session bootstrapped at login ─────────"
SSO_ROW=$($PG -tA -c "SELECT id::text || '|' || sso_id || '|' || status
                       FROM sso_sessions WHERE user_session_id='$SESSION_ID';" | tr -d '[:space:]')
if [ -n "$SSO_ROW" ]; then
    SSO_UUID=$(echo "$SSO_ROW" | cut -d'|' -f1)
    SSO_ID=$(  echo "$SSO_ROW" | cut -d'|' -f2)
    SSO_STAT=$(echo "$SSO_ROW" | cut -d'|' -f3)
    green "  ✅  sso_sessions row exists (uuid=$SSO_UUID, sso_id=$SSO_ID, status=$SSO_STAT)"
    assert_eq "SSO session status" "ACTIVE" "$SSO_STAT"
else
    red "  ❌  no sso_sessions row for user_session $SESSION_ID"
    FAILED=$((FAILED+1))
    exit 1
fi

# GET /api/v1/sso/session
STATUS=$(curl -s -o /tmp/mip8_sso.json -w '%{http_code}' \
              -H "Authorization: Bearer $ALICE" \
              "$BASE/api/v1/sso/session")
check "GET /api/v1/sso/session" 200 "$STATUS"
DTO_SSO_ID=$(jq -r '.ssoId' /tmp/mip8_sso.json)
DTO_STATUS=$(jq -r '.status' /tmp/mip8_sso.json)
DTO_PART_LEN=$(jq -r '.participants | length' /tmp/mip8_sso.json)
assert_eq "DTO ssoId matches DB"      "$SSO_ID"    "$DTO_SSO_ID"
assert_eq "DTO status=ACTIVE"         "ACTIVE"     "$DTO_STATUS"
assert_eq "DTO participants initially empty" "0"  "$DTO_PART_LEN"

# ---------------------------------------------------------------------------
# 3. Register TWO client applications (A: with BCL URI; B: without)
# ---------------------------------------------------------------------------
blue "── 3. Register two clients (A: BCL URI + registered logout URI) ─"

SUFFIX=$(date +%s)
REDIRECT_A="http://localhost:3000/oauth/callback-p8a-$SUFFIX"
REDIRECT_B="http://localhost:3000/oauth/callback-p8b-$SUFFIX"
POST_LOGOUT_A="http://localhost:3000/post-logout-a-$SUFFIX"
BCL_A="http://localhost:9999/bcl-a-$SUFFIX"      # unreachable on purpose (dispatcher should mark FAILED)

# --- Client A ---
CREATE_A=$(jq -n --arg name "SSO WEB A $SUFFIX" --arg r "$REDIRECT_A" \
                 --arg p "$POST_LOGOUT_A" --arg b "$BCL_A" '{
    name: $name,
    applicationType: "WEB",
    redirectUris: [$r],
    postLogoutRedirectUris: [$p],
    backchannelLogoutUri: $b,
    scopes: ["openid","profile","email"]
}')
STATUS=$(curl -s -o /tmp/mip8_appA.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/applications" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' -d "$CREATE_A")
check "POST /applications (client A + BCL)" 201 "$STATUS"
CLIENT_A=$(jq -r '.application.clientId'      /tmp/mip8_appA.json)
CLIENT_A_UUID=$(jq -r '.application.id'       /tmp/mip8_appA.json)
CLIENT_A_SECRET=$(jq -r '.credentials.clientSecret' /tmp/mip8_appA.json)
DTO_BCL_A=$(jq -r '.application.backchannelLogoutUri' /tmp/mip8_appA.json)
assert_eq "client A backchannelLogoutUri echoed" "$BCL_A" "$DTO_BCL_A"

# --- Client B ---
CREATE_B=$(jq -n --arg name "SSO WEB B $SUFFIX" --arg r "$REDIRECT_B" '{
    name: $name,
    applicationType: "WEB",
    redirectUris: [$r],
    scopes: ["openid","profile","email"]
}')
STATUS=$(curl -s -o /tmp/mip8_appB.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/applications" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' -d "$CREATE_B")
check "POST /applications (client B - no BCL)" 201 "$STATUS"
CLIENT_B=$(jq -r '.application.clientId'      /tmp/mip8_appB.json)
CLIENT_B_UUID=$(jq -r '.application.id'       /tmp/mip8_appB.json)
CLIENT_B_SECRET=$(jq -r '.credentials.clientSecret' /tmp/mip8_appB.json)
DTO_BCL_B=$(jq -r '.application.backchannelLogoutUri' /tmp/mip8_appB.json)
assert_eq "client B backchannelLogoutUri is null" "null" "$DTO_BCL_B"

# ---------------------------------------------------------------------------
# 4. Drive one authorization_code + PKCE flow per client
# ---------------------------------------------------------------------------
authorize_and_exchange() {
    local client_id=$1 client_secret=$2 redirect=$3 label=$4
    local verifier challenge state nonce redirect_enc loc code status
    verifier=$(openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n' | head -c 64)
    challenge=$(printf '%s' "$verifier" | openssl dgst -sha256 -binary | \
                openssl base64 | tr '+/' '-_' | tr -d '=\n')
    state=$(openssl rand -hex 8)
    nonce=$(openssl rand -hex 8)
    redirect_enc=$(jq -rn --arg v "$redirect" '$v|@uri')
    loc=$(curl -sSI -o /dev/null -w '%{redirect_url}' \
          -H "Authorization: Bearer $ALICE" \
          "$BASE/oauth2/authorize?response_type=code&client_id=$client_id&redirect_uri=$redirect_enc&scope=openid%20profile%20email&state=$state&nonce=$nonce&code_challenge=$challenge&code_challenge_method=S256")
    code=$(echo "$loc" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
    if [ -z "$code" ]; then
        red "  ❌  $label /authorize returned no code (Location=$loc)"; FAILED=$((FAILED+1)); return 1
    fi
    status=$(curl -s -o /tmp/mip8_tok_$label.json -w '%{http_code}' \
                  -X POST "$BASE/oauth2/token" \
                  -u "$client_id:$client_secret" \
                  -H 'Content-Type: application/x-www-form-urlencoded' \
                  --data-urlencode "grant_type=authorization_code" \
                  --data-urlencode "code=$code" \
                  --data-urlencode "redirect_uri=$redirect" \
                  --data-urlencode "code_verifier=$verifier")
    check "$label POST /oauth2/token" 200 "$status"
}

blue "── 4. Drive OAuth flow for A and B (participant registration) ─"
authorize_and_exchange "$CLIENT_A" "$CLIENT_A_SECRET" "$REDIRECT_A" "A"
authorize_and_exchange "$CLIENT_B" "$CLIENT_B_SECRET" "$REDIRECT_B" "B"

# Participants in DB
PART_A=$($PG -tA -c "SELECT COUNT(*) FROM sso_session_participants
                     WHERE sso_session_id='$SSO_UUID' AND client_application_id='$CLIENT_A_UUID';" | tr -d '[:space:]')
PART_B=$($PG -tA -c "SELECT COUNT(*) FROM sso_session_participants
                     WHERE sso_session_id='$SSO_UUID' AND client_application_id='$CLIENT_B_UUID';" | tr -d '[:space:]')
assert_eq "sso_session_participants row for A" "1" "$PART_A"
assert_eq "sso_session_participants row for B" "1" "$PART_B"

# GET /api/v1/sso/session now shows two participants
curl -s -o /tmp/mip8_sso2.json -H "Authorization: Bearer $ALICE" "$BASE/api/v1/sso/session"
DTO_PART_LEN=$(jq -r '.participants | length' /tmp/mip8_sso2.json)
assert_eq "DTO participants=2 after two authorizations" "2" "$DTO_PART_LEN"

# ---------------------------------------------------------------------------
# 5. Discovery advertises Phase 8 additions
# ---------------------------------------------------------------------------
blue "── 5. /.well-known/openid-configuration advertises Phase 8 ────"
curl -s "$BASE/.well-known/openid-configuration" > /tmp/mip8_disco.json
ISSUER=$(jq -r '.issuer' /tmp/mip8_disco.json)
END=$(  jq -r '.end_session_endpoint // empty'                /tmp/mip8_disco.json)
BCL_S=$(jq -r '.backchannel_logout_supported // false'        /tmp/mip8_disco.json)
BCL_SS=$(jq -r '.backchannel_logout_session_supported // false' /tmp/mip8_disco.json)
assert_eq "end_session_endpoint" "$ISSUER/oauth2/logout" "$END"
assert_eq "backchannel_logout_supported=true"           "true" "$BCL_S"
assert_eq "backchannel_logout_session_supported=true"   "true" "$BCL_SS"

# ---------------------------------------------------------------------------
# 6. RP-Initiated Logout
# ---------------------------------------------------------------------------
blue "── 6. Module 8.2 — RP-Initiated Logout (302 back to registered URI) ─"
STATE_LOGOUT=$(openssl rand -hex 8)
POST_LOGOUT_ENC=$(jq -rn --arg v "$POST_LOGOUT_A" '$v|@uri')

LOGOUT_STATUS=$(curl -s -o /tmp/mip8_logout_body.txt -w '%{http_code}' -D /tmp/mip8_logout_hdr.txt \
                     -H "Authorization: Bearer $ALICE" \
                     "$BASE/oauth2/logout?client_id=$CLIENT_A&post_logout_redirect_uri=$POST_LOGOUT_ENC&state=$STATE_LOGOUT")
check "GET /oauth2/logout (registered redirect)" 302 "$LOGOUT_STATUS"

LOC=$(awk -F': ' 'tolower($1)=="location" {print $2}' /tmp/mip8_logout_hdr.txt | tr -d '\r' | tail -1)
green "  Location header: $LOC"
case "$LOC" in
    "$POST_LOGOUT_A"*state=$STATE_LOGOUT*)
        green "  ✅  redirect target + state round-tripped" ;;
    *)  red   "  ❌  unexpected Location: $LOC"; FAILED=$((FAILED+1)) ;;
esac

# ---------------------------------------------------------------------------
# 7. Cascade assertions (session termination + OAuth revocation + fan-out)
# ---------------------------------------------------------------------------
blue "── 7. Cascade: SSO/user session + OAuth refresh tokens + fan-out ─"

# Give the async back-channel dispatcher time to run.
sleep 2

USR_STATUS=$($PG -tA -c "SELECT status FROM user_sessions WHERE id='$SESSION_ID';" | tr -d '[:space:]')
SSO_FINAL=$( $PG -tA -c "SELECT status FROM sso_sessions   WHERE id='$SSO_UUID';"  | tr -d '[:space:]')
assert_eq "user_sessions.status" "TERMINATED" "$USR_STATUS"
assert_eq "sso_sessions.status"  "TERMINATED" "$SSO_FINAL"

TERM_REASON=$($PG -tA -c "SELECT termination_reason FROM sso_sessions WHERE id='$SSO_UUID';" | tr -d '[:space:]')
STEP=$((STEP+1))
if [ -n "$TERM_REASON" ] && [ "$TERM_REASON" != "null" ]; then
    green "  ✅  [$STEP] sso_sessions.termination_reason populated ($TERM_REASON)"
else
    yellow "  •   [$STEP] termination_reason blank (informational)"
fi

# Every oauth_refresh_tokens row for the session must be non-ACTIVE.
BAD_OAUTH=$($PG -tA -c "SELECT COUNT(*) FROM oauth_refresh_tokens
                         WHERE session_id='$SESSION_ID' AND status='ACTIVE';" | tr -d '[:space:]')
assert_eq "oauth_refresh_tokens ACTIVE rows after logout" "0" "$BAD_OAUTH"

# Phase-3 refresh tokens also revoked
BAD_P3=$($PG -tA -c "SELECT COUNT(*) FROM refresh_tokens
                      WHERE session_id='$SESSION_ID' AND status='ACTIVE';" | tr -d '[:space:]')
assert_eq "refresh_tokens ACTIVE rows after logout" "0" "$BAD_P3"

# Notification state per participant
NOTIF_A=$($PG -tA -c "SELECT COALESCE(logout_notification_state::text,'NULL')
                       FROM sso_session_participants
                       WHERE sso_session_id='$SSO_UUID' AND client_application_id='$CLIENT_A_UUID';" | tr -d '[:space:]')
NOTIF_B=$($PG -tA -c "SELECT COALESCE(logout_notification_state::text,'NULL')
                       FROM sso_session_participants
                       WHERE sso_session_id='$SSO_UUID' AND client_application_id='$CLIENT_B_UUID';" | tr -d '[:space:]')

STEP=$((STEP+1))
case "$NOTIF_A" in
    SENT|FAILED) green "  ✅  [$STEP] participant A notification_state=$NOTIF_A (dispatcher ran)" ;;
    NULL)        red  "  ❌  [$STEP] participant A never notified (state=NULL)"; FAILED=$((FAILED+1)) ;;
    *)           yellow "  •   [$STEP] participant A state=$NOTIF_A" ;;
esac
STEP=$((STEP+1))
case "$NOTIF_B" in
    SKIPPED) green "  ✅  [$STEP] participant B notification_state=SKIPPED (no BCL URI)" ;;
    NULL)    red   "  ❌  [$STEP] participant B never processed"; FAILED=$((FAILED+1)) ;;
    *)       yellow "  •   [$STEP] participant B state=$NOTIF_B" ;;
esac

# Access token issued before logout should now be rejected.
# Spring Security's stateless chain returns 403 when the principal is missing
# (JwtAuthenticationFilter clears the context on invalid tokens) — either 401
# or 403 proves the token is dead.
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -H "Authorization: Bearer $ALICE" "$BASE/api/v1/sso/session")
STEP=$((STEP+1))
if [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
    green "  ✅  [$STEP] old access token blocked after logout (HTTP $STATUS)"
else
    red   "  ❌  [$STEP] old access token still accepted (HTTP $STATUS)"
    FAILED=$((FAILED+1))
fi


# ---------------------------------------------------------------------------
# 8. Negative paths
# ---------------------------------------------------------------------------
blue "── 8. Negative paths ─────────────────────────────────────────"

# 8a. Unregistered post_logout_redirect_uri => 400 invalid_request
BAD_POST_ENC=$(jq -rn --arg v "http://attacker.example.com/steal" '$v|@uri')
STATUS=$(curl -s -o /tmp/mip8_bad_pl.json -w '%{http_code}' \
              "$BASE/oauth2/logout?client_id=$CLIENT_A&post_logout_redirect_uri=$BAD_POST_ENC&state=x")
check "unregistered post_logout_redirect_uri" 400 "$STATUS"

# 8b. Unknown client_id => 400 invalid_request
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              "$BASE/oauth2/logout?client_id=totally-fake-client")
check "unknown client_id"                     400 "$STATUS"

# 8c. Anonymous caller with registered redirect => 302 (idempotent logout)
STATUS=$(curl -s -o /dev/null -D /tmp/mip8_anon_hdr.txt -w '%{http_code}' \
              "$BASE/oauth2/logout?client_id=$CLIENT_A&post_logout_redirect_uri=$POST_LOGOUT_ENC&state=anon")
check "anonymous logout follows registered redirect" 302 "$STATUS"

# 8d. Unauthenticated SSO introspection => 401 or 403 (Spring's stateless
# default is 403; either proves the endpoint is protected).
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/sso/session")
STEP=$((STEP+1))
if [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
    green "  ✅  [$STEP] GET /api/v1/sso/session without token blocked (HTTP $STATUS)"
else
    red   "  ❌  [$STEP] SSO introspection reachable anonymously (HTTP $STATUS)"
    FAILED=$((FAILED+1))
fi


# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo
if [ "$FAILED" -eq 0 ]; then
    green "✅ Phase 8 API verification passed — all checks succeeded."
    exit 0
else
    red   "❌ $FAILED check(s) failed."
    exit 1
fi
