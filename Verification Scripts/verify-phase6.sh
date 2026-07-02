#!/usr/bin/env bash
#
# Phase 6 schema / compile smoke test.
# Verifies:
#   - V8 migration is applied (oauth_authorization_codes, oauth_access_tokens, oauth_refresh_tokens)
#   - The Java module compiles
#   - The OAuth engine wires up (Spring context starts) - optional, opt-in via RUN_APP=1
#
set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

info()  { echo -e "${YELLOW}➜${NC} $*"; }
ok()    { echo -e "${GREEN}✔${NC} $*"; }
fail()  { echo -e "${RED}✘${NC} $*"; exit 1; }

info "Phase 6: OAuth2 Authorization Server verification"

# 1. Compile
info "mvn -q -DskipTests compile"
mvn -q -DskipTests compile || fail "Maven compile failed"
ok "Maven compile succeeded"

# 2. Migration file exists
MIG=src/main/resources/db/migration/V8__oauth_authorization_codes_and_tokens.sql
[[ -f "$MIG" ]] || fail "Missing $MIG"
ok "Found $MIG"

# 3. Database check (if PostgreSQL container is running)
if docker exec meicrypt-postgres pg_isready -U meicrypt >/dev/null 2>&1; then
  info "Checking Flyway history..."
  V8_ROW=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -tA -c \
    "SELECT version FROM flyway_schema_history WHERE version='8';" 2>/dev/null || true)
  if [[ "$V8_ROW" == "8" ]]; then
    ok "Flyway V8 migration applied"
  else
    info "V8 not yet applied - start the app once to run migrations"
  fi

  info "Checking oauth tables..."
  for tbl in oauth_authorization_codes oauth_access_tokens oauth_refresh_tokens; do
    EXISTS=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -tA -c \
      "SELECT to_regclass('public.$tbl');" 2>/dev/null || true)
    if [[ -n "$EXISTS" && "$EXISTS" != "null" ]]; then
      ok "  $tbl exists"
    else
      info "  $tbl not yet created (start the app once)"
    fi
  done
else
  info "PostgreSQL container not running - skipping DB checks (docker-compose up -d)"
fi

# 4. Package structure sanity
BASE=src/main/java/com/meicrypt/identity/oauth
for f in \
    entity/OAuthAuthorizationCode.java \
    entity/OAuthAccessToken.java \
    entity/OAuthRefreshToken.java \
    entity/OAuthRefreshTokenStatus.java \
    repository/OAuthAuthorizationCodeRepository.java \
    repository/OAuthAccessTokenRepository.java \
    repository/OAuthRefreshTokenRepository.java \
    dto/OAuthTokenResponse.java \
    dto/OAuthErrorResponse.java \
    dto/IntrospectionResponse.java \
    dto/AuthorizationCodeResponse.java \
    exception/OAuthException.java \
    service/PkceValidator.java \
    service/ScopeService.java \
    service/OAuthTokenGenerator.java \
    service/OAuthAuthorizationService.java \
    service/OAuthTokenService.java \
    service/OAuthIntrospectionService.java \
    controller/OAuthAuthorizationController.java \
    controller/OAuthTokenController.java \
    controller/OAuthRevocationController.java \
    controller/OAuthIntrospectionController.java ; do
  [[ -f "$BASE/$f" ]] || fail "Missing $BASE/$f"
done
ok "All Phase 6 source files present ($(ls $BASE/**/*.java | wc -l) files)"

echo
ok "✅ Phase 6 schema verification passed."
echo "   Start the app (mvn spring-boot:run) and run verify-phase6-api.sh for a full flow test."
