#!/usr/bin/env bash
#
# Phase 7 schema / compile smoke test.
# Verifies:
#   - V9 migration is applied (oauth_signing_keys)
#   - RSA signing-key entity, repository, service, and controllers exist
#   - The Java module compiles
#
set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

info()  { echo -e "${YELLOW}➜${NC} $*"; }
ok()    { echo -e "${GREEN}✔${NC} $*"; }
fail()  { echo -e "${RED}✘${NC} $*"; exit 1; }

info "Phase 7: OIDC Discovery + JWKS verification"

# 1. Compile
info "mvn -q -DskipTests compile"
mvn -q -DskipTests compile || fail "Maven compile failed"
ok "Maven compile succeeded"

# 2. Migration file exists
MIG=src/main/resources/db/migration/V9__oauth_signing_keys.sql
[[ -f "$MIG" ]] || fail "Missing $MIG"
ok "Found $MIG"

# 3. Database check (if PostgreSQL container is running)
if docker exec meicrypt-postgres pg_isready -U meicrypt >/dev/null 2>&1; then
  info "Checking Flyway history..."
  V9_ROW=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -tA -c \
    "SELECT version FROM flyway_schema_history WHERE version='9';" 2>/dev/null || true)
  if [[ "$V9_ROW" == "9" ]]; then
    ok "Flyway V9 migration applied"
  else
    info "V9 not yet applied - start the app once to run migrations"
  fi

  info "Checking oauth_signing_keys table..."
  EXISTS=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -tA -c \
    "SELECT to_regclass('public.oauth_signing_keys');" 2>/dev/null || true)
  if [[ -n "$EXISTS" && "$EXISTS" != "null" ]]; then
    ok "  oauth_signing_keys exists"
  else
    info "  oauth_signing_keys not yet created (start the app once)"
  fi

  info "Checking ACTIVE signing key row..."
  ACTIVE_COUNT=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -tA -c \
    "SELECT COUNT(*) FROM oauth_signing_keys WHERE status='ACTIVE';" 2>/dev/null || echo 0)
  if [[ "${ACTIVE_COUNT:-0}" -ge 1 ]]; then
    ok "  ACTIVE signing key present ($ACTIVE_COUNT row(s))"
  else
    info "  no ACTIVE signing key yet - it will be bootstrapped on next startup"
  fi
else
  info "PostgreSQL container not running - skipping DB checks (docker-compose up -d)"
fi

# 4. Package structure sanity
BASE=src/main/java/com/meicrypt/identity/oauth
for f in \
    entity/OAuthSigningKey.java \
    entity/OAuthSigningKeyStatus.java \
    repository/OAuthSigningKeyRepository.java \
    service/OAuthSigningKeyService.java \
    dto/JwkDTO.java \
    dto/JwksResponse.java \
    dto/OpenIdConfigurationResponse.java \
    controller/JwksController.java \
    controller/OpenIdDiscoveryController.java ; do
  [[ -f "$BASE/$f" ]] || fail "Missing $BASE/$f"
done
ok "All Phase 7 source files present"

# 5. Live HTTP probe (if the app is running)
BASE_URL=${BASE_URL:-http://localhost:8080}
if curl -fs --max-time 2 "$BASE_URL/actuator/health" >/dev/null 2>&1; then
  info "App is up on $BASE_URL - probing discovery endpoints"

  DISCO=$(curl -fs "$BASE_URL/.well-known/openid-configuration" || true)
  if [[ -n "$DISCO" ]]; then
    ok "  /.well-known/openid-configuration responded"
    echo "$DISCO" | jq -r '. | "    issuer:            " + .issuer + "\n" +
                             "    authorization ep:  " + .authorization_endpoint + "\n" +
                             "    token ep:          " + .token_endpoint + "\n" +
                             "    jwks_uri:          " + .jwks_uri + "\n" +
                             "    id_token algs:     " + (.id_token_signing_alg_values_supported | join(","))'
  else
    info "  /.well-known/openid-configuration did not respond"
  fi

  JWKS=$(curl -fs "$BASE_URL/.well-known/jwks.json" || true)
  if [[ -n "$JWKS" ]]; then
    KID=$(echo "$JWKS" | jq -r '.keys[0].kid // empty')
    N_LEN=$(echo "$JWKS" | jq -r '.keys[0].n // empty' | wc -c)
    if [[ -n "$KID" ]]; then
      ok "  /.well-known/jwks.json exposes kid=$KID (modulus b64 len=$N_LEN)"
    else
      info "  /.well-known/jwks.json returned an empty key set"
    fi
  else
    info "  /.well-known/jwks.json did not respond"
  fi
else
  info "App not reachable at $BASE_URL - skipping live probes (mvn spring-boot:run)"
fi

echo
ok "✅ Phase 7 verification complete."
echo "   Start the app (mvn spring-boot:run) and re-run to see full JWKS output."
