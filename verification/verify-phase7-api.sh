#!/bin/bash
# ============================================================================
# MeiCrypt Identity Platform - Phase 7 (OIDC Discovery + JWKS)
# end-to-end API verification.
# ----------------------------------------------------------------------------
# What it does:
#   1. Ensures a Phase 6 confidential WEB client exists (re-uses the harness
#      style of verify-phase6-api.sh) and Alice can log in.
#   2. Exercises the two Phase 7 modules against the running app:
#        7.1  /.well-known/openid-configuration  (discovery advertises the
#             correct endpoints, algorithms, response types, and PKCE method)
#        7.2  /.well-known/jwks.json             (RSA public key advertised,
#             matches the kid stamped into freshly-issued JWTs, and the
#             modulus/exponent verify the signature end-to-end)
#   3. Verifies negative paths:
#        - discovery endpoints must be publicly reachable (no auth header
#          required and no 401 emitted)
#        - JWKS must NEVER leak private key material (`d` claim absent)
#        - the `iss` claim of every issued token equals the discovery issuer
#        - JWT signature verifies with the public key from JWKS via openssl
#
# Requirements: docker (postgres container "meicrypt-postgres"), curl, jq,
#               openssl, awk, python3 (for base64url arithmetic decode).
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
assert() {
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
require awk
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

V9=$($PG -tA -c "SELECT COUNT(*) FROM flyway_schema_history WHERE version='9' AND success=true;" 2>/dev/null | tr -d '[:space:]')
if [ "$V9" != "1" ]; then
    red "Flyway V9 not applied yet — is the app running with the Phase 7 sources? Aborting."
    exit 1
fi
green "V9 migration applied"

ACTIVE_KID=$($PG -tA -c "SELECT kid FROM oauth_signing_keys WHERE status='ACTIVE' ORDER BY created_at DESC LIMIT 1;" 2>/dev/null | tr -d '[:space:]')
if [ -z "$ACTIVE_KID" ]; then
    red "No ACTIVE signing key row in oauth_signing_keys — bootstrap failed."
    exit 1
fi
green "Active signing key present in DB: kid=$ACTIVE_KID"

# ---------------------------------------------------------------------------
# 1. Fetch discovery document
# ---------------------------------------------------------------------------
blue "── 1. Module 7.1 — /.well-known/openid-configuration ────────"
STATUS=$(curl -s -o /tmp/mip_disco.json -w '%{http_code}' "$BASE/.well-known/openid-configuration")
check "GET /.well-known/openid-configuration (no auth)" 200 "$STATUS"

ISSUER=$(jq -r '.issuer'                                  /tmp/mip_disco.json)
AUTHZ=$( jq -r '.authorization_endpoint'                  /tmp/mip_disco.json)
TOKEN=$( jq -r '.token_endpoint'                          /tmp/mip_disco.json)
INTRO=$( jq -r '.introspection_endpoint'                  /tmp/mip_disco.json)
REVOKE=$(jq -r '.revocation_endpoint'                     /tmp/mip_disco.json)
JWKS_URI=$(jq -r '.jwks_uri'                              /tmp/mip_disco.json)
RESP=$(   jq -r '.response_types_supported | join(",")'   /tmp/mip_disco.json)
GRANTS=$( jq -r '.grant_types_supported    | join(",")'   /tmp/mip_disco.json)
ALGS=$(   jq -r '.id_token_signing_alg_values_supported | join(",")' /tmp/mip_disco.json)
PKCE=$(   jq -r '.code_challenge_methods_supported | join(",")'      /tmp/mip_disco.json)

[ "$ISSUER" != "null" ] && [ -n "$ISSUER" ]        && green "  ✅  issuer=$ISSUER"                    || { red "  ❌  issuer missing"; FAILED=$((FAILED+1)); }
[ "$AUTHZ"  = "$ISSUER/oauth2/authorize" ]         && green "  ✅  authorization_endpoint OK"         || { red "  ❌  authz=$AUTHZ"; FAILED=$((FAILED+1)); }
[ "$TOKEN"  = "$ISSUER/oauth2/token" ]             && green "  ✅  token_endpoint OK"                 || { red "  ❌  token=$TOKEN"; FAILED=$((FAILED+1)); }
[ "$INTRO"  = "$ISSUER/oauth2/introspect" ]        && green "  ✅  introspection_endpoint OK"         || { red "  ❌  introspect=$INTRO"; FAILED=$((FAILED+1)); }
[ "$REVOKE" = "$ISSUER/oauth2/revoke" ]            && green "  ✅  revocation_endpoint OK"            || { red "  ❌  revoke=$REVOKE"; FAILED=$((FAILED+1)); }
[ "$JWKS_URI" = "$ISSUER/.well-known/jwks.json" ]  && green "  ✅  jwks_uri OK"                       || { red "  ❌  jwks=$JWKS_URI"; FAILED=$((FAILED+1)); }
[ "$RESP"   = "code" ]                             && green "  ✅  response_types_supported=[code]"   || { red "  ❌  response_types=$RESP"; FAILED=$((FAILED+1)); }
case "$GRANTS" in
    *authorization_code*refresh_token*|*refresh_token*authorization_code*)
        green "  ✅  grant_types_supported contains authorization_code + refresh_token";;
    *)  red   "  ❌  grant_types=$GRANTS"; FAILED=$((FAILED+1));;
esac
[ "$ALGS"   = "RS256" ]                            && green "  ✅  id_token_signing_alg_values_supported=[RS256]" || { red "  ❌  algs=$ALGS"; FAILED=$((FAILED+1)); }
[ "$PKCE"   = "S256" ]                             && green "  ✅  code_challenge_methods_supported=[S256]"       || { red "  ❌  pkce=$PKCE"; FAILED=$((FAILED+1)); }

# ---------------------------------------------------------------------------
# 2. Fetch JWKS document
# ---------------------------------------------------------------------------
blue "── 2. Module 7.2 — /.well-known/jwks.json ───────────────────"
STATUS=$(curl -s -o /tmp/mip_jwks.json -w '%{http_code}' "$JWKS_URI")
check "GET /.well-known/jwks.json (no auth)" 200 "$STATUS"

KEY_COUNT=$(jq -r '.keys | length'                                   /tmp/mip_jwks.json)
PRIMARY_KID=$(jq -r --arg kid "$ACTIVE_KID" '.keys[] | select(.kid==$kid) | .kid' /tmp/mip_jwks.json)
KTY=$(       jq -r --arg kid "$ACTIVE_KID" '.keys[] | select(.kid==$kid) | .kty' /tmp/mip_jwks.json)
ALG=$(       jq -r --arg kid "$ACTIVE_KID" '.keys[] | select(.kid==$kid) | .alg' /tmp/mip_jwks.json)
USE=$(       jq -r --arg kid "$ACTIVE_KID" '.keys[] | select(.kid==$kid) | .use' /tmp/mip_jwks.json)
N=$(         jq -r --arg kid "$ACTIVE_KID" '.keys[] | select(.kid==$kid) | .n'   /tmp/mip_jwks.json)
E=$(         jq -r --arg kid "$ACTIVE_KID" '.keys[] | select(.kid==$kid) | .e'   /tmp/mip_jwks.json)

[ "$KEY_COUNT" -ge 1 ]        && green "  ✅  JWKS contains $KEY_COUNT key(s)"                            || { red "  ❌  no keys"; FAILED=$((FAILED+1)); }
[ "$PRIMARY_KID" = "$ACTIVE_KID" ] && green "  ✅  ACTIVE kid $ACTIVE_KID published"                       || { red "  ❌  active kid not in JWKS"; FAILED=$((FAILED+1)); }
[ "$KTY" = "RSA" ]            && green "  ✅  kty=RSA"                                                    || { red "  ❌  kty=$KTY"; FAILED=$((FAILED+1)); }
[ "$ALG" = "RS256" ]          && green "  ✅  alg=RS256"                                                  || { red "  ❌  alg=$ALG"; FAILED=$((FAILED+1)); }
[ "$USE" = "sig" ]             && green "  ✅  use=sig"                                                   || { red "  ❌  use=$USE"; FAILED=$((FAILED+1)); }
[ "$E"   = "AQAB" ]           && green "  ✅  e=AQAB (65537)"                                             || { red "  ❌  e=$E"; FAILED=$((FAILED+1)); }
[ ${#N} -ge 300 ]             && green "  ✅  modulus length ${#N} chars (RSA-2048)"                     || { red "  ❌  modulus too short (${#N})"; FAILED=$((FAILED+1)); }

# Ensure NO private material leaks - the 'd', 'p', 'q' fields must be absent.
LEAK=$(jq -r '.keys[] | select(.d or .p or .q or .dp or .dq or .qi) | .kid // empty' /tmp/mip_jwks.json)
if [ -z "$LEAK" ]; then
    green "  ✅  no private-key fields (d/p/q/dp/dq/qi) present in JWKS"
else
    red   "  ❌  JWKS leaks private-key material for kid=$LEAK"; FAILED=$((FAILED+1))
fi

# Sanity: `Cache-Control: public` is set (long-lived caching allowed)
CC=$(curl -sI "$JWKS_URI" | awk -F': ' '/^[Cc]ache-[Cc]ontrol:/ {print $2}' | tr -d '\r')
case "$CC" in *public*max-age*) green "  ✅  Cache-Control: $CC";; *) yellow "  •   Cache-Control='$CC'";; esac

# ---------------------------------------------------------------------------
# 3. Alice logs in (Phase 3) so we can drive /oauth2/authorize
# ---------------------------------------------------------------------------
blue "── 3. Login Alice + register a WEB client (reused from P6) ───"

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
        *)       yellow "  Register status $resp for $email";;
    esac
}
register_user alice@example.com Alice Doe

# Auto-verify alice via DB token if still PENDING
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
                    '{organizationId:$o, email:$e, password:$p}')
ALICE=$(curl -s -X POST "$BASE/api/v1/auth/login" \
             -H 'Content-Type: application/json' -d "$LOGIN_BODY" \
        | jq -r '.accessToken // empty')
[ -n "$ALICE" ] || { red "Alice login failed"; exit 1; }
green "Alice token acquired"

SUFFIX=$(date +%s)
REDIRECT="http://localhost:3000/oauth/callback-p7-$SUFFIX"
CREATE_BODY=$(jq -n --arg name "OIDC WEB $SUFFIX" --arg r "$REDIRECT" '{
    name: $name,
    applicationType: "WEB",
    redirectUris: [$r],
    scopes: ["openid","profile","email"]
}')
STATUS=$(curl -s -o /tmp/mip_client.json -w '%{http_code}' \
              -X POST "$BASE/api/v1/organizations/$ORG/applications" \
              -H "Authorization: Bearer $ALICE" \
              -H 'Content-Type: application/json' \
              -d "$CREATE_BODY")
check "POST /applications (WEB)" 201 "$STATUS"
CLIENT_ID=$(jq -r '.application.clientId'     /tmp/mip_client.json)
CLIENT_SECRET=$(jq -r '.credentials.clientSecret' /tmp/mip_client.json)
green "  client_id=$CLIENT_ID"

# ---------------------------------------------------------------------------
# 4. Drive a full authorization_code + PKCE flow, then inspect the JWTs
# ---------------------------------------------------------------------------
blue "── 4. Full OIDC flow: /authorize -> /token -> RS256 tokens ──"
VERIFIER=$(openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n' | head -c 64)
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | \
            openssl base64 | tr '+/' '-_' | tr -d '=\n')
STATE=$(openssl rand -hex 8)
NONCE=$(openssl rand -hex 8)
REDIRECT_ENC=$(jq -rn --arg v "$REDIRECT" '$v|@uri')

LOC=$(curl -sSI -o /dev/null -w '%{redirect_url}' \
      -H "Authorization: Bearer $ALICE" \
      "$BASE/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_ENC&scope=openid%20profile%20email&state=$STATE&nonce=$NONCE&code_challenge=$CHALLENGE&code_challenge_method=S256")
CODE=$(echo "$LOC" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
[ -n "$CODE" ] && green "  ✅  authorization code obtained" || { red "  ❌  no code returned"; FAILED=$((FAILED+1)); }

STATUS=$(curl -s -o /tmp/mip_tok.json -w '%{http_code}' \
              -X POST "$BASE/oauth2/token" \
              -u "$CLIENT_ID:$CLIENT_SECRET" \
              -H 'Content-Type: application/x-www-form-urlencoded' \
              --data-urlencode "grant_type=authorization_code" \
              --data-urlencode "code=$CODE" \
              --data-urlencode "redirect_uri=$REDIRECT" \
              --data-urlencode "code_verifier=$VERIFIER")
check "POST /oauth2/token (code)" 200 "$STATUS"
AT=$(jq -r '.access_token' /tmp/mip_tok.json)
IDT=$(jq -r '.id_token'    /tmp/mip_tok.json)

# ---------------------------------------------------------------------------
# 5. Access token: RS256 header + kid + iss claim
# ---------------------------------------------------------------------------
blue "── 5. Access token header/claims match discovery & JWKS ─────"

b64url_decode() {
    # $1 = base64url string; adds padding then base64 -d.
    local s=${1//-/+}; s=${s//_//}
    local mod=$(( ${#s} % 4 )); [ $mod -eq 2 ] && s="${s}=="; [ $mod -eq 3 ] && s="${s}="
    printf '%s' "$s" | base64 -d 2>/dev/null
}

AT_HDR=$(b64url_decode "$(echo "$AT" | cut -d. -f1)")
AT_PAY=$(b64url_decode "$(echo "$AT" | cut -d. -f2)")

AT_ALG=$(echo "$AT_HDR" | jq -r .alg)
AT_KID=$(echo "$AT_HDR" | jq -r .kid)
AT_ISS=$(echo "$AT_PAY" | jq -r .iss)
# `aud` may be either a string (single audience) or an array (RFC 7519 §4.1.3).
AT_AUD=$(echo "$AT_PAY" | jq -r 'if (.aud|type=="array") then .aud[0] else .aud end')
AT_SUB=$(echo "$AT_PAY" | jq -r .sub)

[ "$AT_ALG" = "RS256" ]      && green "  ✅  access_token alg=RS256"                        || { red "  ❌  alg=$AT_ALG"; FAILED=$((FAILED+1)); }
[ "$AT_KID" = "$ACTIVE_KID" ] && green "  ✅  access_token kid=$AT_KID matches JWKS"         || { red "  ❌  kid mismatch: $AT_KID vs $ACTIVE_KID"; FAILED=$((FAILED+1)); }
[ "$AT_ISS" = "$ISSUER" ]    && green "  ✅  access_token iss matches discovery"            || { red "  ❌  iss=$AT_ISS vs discovery=$ISSUER"; FAILED=$((FAILED+1)); }
[ "$AT_AUD" = "$CLIENT_ID" ] && green "  ✅  access_token aud=client_id"                   || { red "  ❌  aud=$AT_AUD"; FAILED=$((FAILED+1)); }
[ -n "$AT_SUB" ] && [ "$AT_SUB" != "null" ] && green "  ✅  access_token sub populated"     || { red "  ❌  sub missing"; FAILED=$((FAILED+1)); }

# ---------------------------------------------------------------------------
# 6. ID token: RS256 header + OIDC-mandated claims + nonce round-trip
# ---------------------------------------------------------------------------
blue "── 6. id_token complies with OIDC Core §2 ───────────────────"
IDT_HDR=$(b64url_decode "$(echo "$IDT" | cut -d. -f1)")
IDT_PAY=$(b64url_decode "$(echo "$IDT" | cut -d. -f2)")

IDT_ALG=$(echo "$IDT_HDR" | jq -r .alg)
IDT_KID=$(echo "$IDT_HDR" | jq -r .kid)
IDT_ISS=$(echo "$IDT_PAY" | jq -r .iss)
IDT_SUB=$(echo "$IDT_PAY" | jq -r .sub)
IDT_AUD=$(echo "$IDT_PAY" | jq -r 'if (.aud|type=="array") then .aud[0] else .aud end')
IDT_EXP=$(echo "$IDT_PAY" | jq -r .exp)
IDT_IAT=$(echo "$IDT_PAY" | jq -r .iat)
IDT_NONCE=$(echo "$IDT_PAY" | jq -r .nonce)
IDT_TT=$(echo "$IDT_PAY"    | jq -r .token_type)

[ "$IDT_ALG"   = "RS256" ]      && green "  ✅  id_token alg=RS256"                          || { red "  ❌  alg=$IDT_ALG"; FAILED=$((FAILED+1)); }
[ "$IDT_KID"   = "$ACTIVE_KID" ] && green "  ✅  id_token kid=$IDT_KID matches JWKS"          || { red "  ❌  kid mismatch"; FAILED=$((FAILED+1)); }
[ "$IDT_ISS"   = "$ISSUER" ]    && green "  ✅  id_token iss=$IDT_ISS"                       || { red "  ❌  iss=$IDT_ISS"; FAILED=$((FAILED+1)); }
[ "$IDT_AUD"   = "$CLIENT_ID" ] && green "  ✅  id_token aud=$IDT_AUD"                       || { red "  ❌  aud=$IDT_AUD"; FAILED=$((FAILED+1)); }
[ -n "$IDT_SUB" ] && [ "$IDT_SUB" != "null" ] && green "  ✅  id_token sub populated"       || { red "  ❌  sub missing"; FAILED=$((FAILED+1)); }
[ -n "$IDT_EXP" ] && [ "$IDT_EXP" != "null" ] && green "  ✅  id_token exp populated"       || { red "  ❌  exp missing"; FAILED=$((FAILED+1)); }
[ -n "$IDT_IAT" ] && [ "$IDT_IAT" != "null" ] && green "  ✅  id_token iat populated"       || { red "  ❌  iat missing"; FAILED=$((FAILED+1)); }
[ "$IDT_NONCE" = "$NONCE" ]     && green "  ✅  id_token nonce round-tripped"                || { red "  ❌  nonce=$IDT_NONCE vs $NONCE"; FAILED=$((FAILED+1)); }
[ "$IDT_TT"    = "id_token" ]   && green "  ✅  id_token token_type=id_token"                || yellow "  •   token_type=$IDT_TT"

# ---------------------------------------------------------------------------
# 7. Verify the RSA signature end-to-end using JWKS (n, e) + openssl
# ---------------------------------------------------------------------------
blue "── 7. RSA signature verified end-to-end via JWKS + openssl ──"

# 7a. Build a PEM public key from the (n, e) advertised by JWKS.
python3 - "$N" "$E" > /tmp/mip_jwks_pub.pem <<'PYEOF'
import base64, sys
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
def b64u(v):
    v += '=' * ((4 - len(v) % 4) % 4)
    return base64.urlsafe_b64decode(v.encode())
n = int.from_bytes(b64u(sys.argv[1]), 'big')
e = int.from_bytes(b64u(sys.argv[2]), 'big')
pub = rsa.RSAPublicNumbers(e, n).public_key()
sys.stdout.buffer.write(pub.public_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PublicFormat.SubjectPublicKeyInfo))
PYEOF

if [ ! -s /tmp/mip_jwks_pub.pem ]; then
    yellow "  •   Skipping openssl signature verification (python 'cryptography' package missing)."
    yellow "      Install with: pip install cryptography  (this check is optional)."
else
    SIGN=$(echo "$AT" | awk -F. '{print $1"."$2}')
    SIGB64URL=$(echo "$AT" | awk -F. '{print $3}')

    # base64url -> base64 -> binary
    SIGB64=$(printf '%s' "$SIGB64URL" | tr '_-' '/+' )
    MOD=$(( ${#SIGB64} % 4 )); [ $MOD -eq 2 ] && SIGB64="${SIGB64}=="; [ $MOD -eq 3 ] && SIGB64="${SIGB64}="
    printf '%s' "$SIGB64" | base64 -d > /tmp/mip_sig.bin
    printf '%s' "$SIGN"   > /tmp/mip_sign_input.txt

    if openssl dgst -sha256 -verify /tmp/mip_jwks_pub.pem \
                    -signature /tmp/mip_sig.bin /tmp/mip_sign_input.txt >/tmp/mip_ossl.out 2>&1
    then
        green "  ✅  openssl verified access_token signature with JWKS public key"
    else
        red   "  ❌  openssl signature verification failed"
        cat /tmp/mip_ossl.out >&2
        FAILED=$((FAILED+1))
    fi
fi

# ---------------------------------------------------------------------------
# 8. Discovery endpoints are truly public (no bearer required)
# ---------------------------------------------------------------------------
blue "── 8. Public reachability of /.well-known/** (no auth) ──────"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -H 'Authorization: '"" \
              "$BASE/.well-known/openid-configuration")
check "GET openid-configuration w/o auth" 200 "$STATUS"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
              -H 'Authorization: '"" \
              "$BASE/.well-known/jwks.json")
check "GET jwks.json w/o auth" 200 "$STATUS"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo
if [ "$FAILED" -eq 0 ]; then
    green "✅ Phase 7 API verification passed — all checks succeeded."
    exit 0
else
    red   "❌ $FAILED check(s) failed."
    exit 1
fi
