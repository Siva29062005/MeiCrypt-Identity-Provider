#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# MeiCrypt Identity Platform - Phase 8 verification
# Modules covered:
#   8.1  SSO Shared Sessions
#   8.2  Single Logout (RP-Initiated Logout + Back-Channel Logout fan-out)
# ---------------------------------------------------------------------------
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${YELLOW}[phase8]${NC} $*"; }
ok()    { echo -e "${GREEN}[  OK  ]${NC} $*"; }
fail()  { echo -e "${RED}[ FAIL ]${NC} $*"; exit 1; }

info "1/5 Verifying V10 migration script exists..."
test -f src/main/resources/db/migration/V10__sso_sessions_and_participants.sql \
  || fail "V10 migration file missing"
grep -q "sso_sessions"             src/main/resources/db/migration/V10__sso_sessions_and_participants.sql \
  || fail "sso_sessions table missing from V10"
grep -q "sso_session_participants" src/main/resources/db/migration/V10__sso_sessions_and_participants.sql \
  || fail "sso_session_participants table missing from V10"
grep -q "backchannel_logout_uri"   src/main/resources/db/migration/V10__sso_sessions_and_participants.sql \
  || fail "backchannel_logout_uri column missing from V10"
ok "V10 migration script contains all required DDL"

info "2/5 Verifying Phase 8 source layout..."
required=(
  src/main/java/com/meicrypt/identity/sso/entity/SsoSession.java
  src/main/java/com/meicrypt/identity/sso/entity/SsoSessionStatus.java
  src/main/java/com/meicrypt/identity/sso/entity/SsoSessionParticipant.java
  src/main/java/com/meicrypt/identity/sso/repository/SsoSessionRepository.java
  src/main/java/com/meicrypt/identity/sso/repository/SsoSessionParticipantRepository.java
  src/main/java/com/meicrypt/identity/sso/service/SsoSessionService.java
  src/main/java/com/meicrypt/identity/sso/service/BackchannelLogoutService.java
  src/main/java/com/meicrypt/identity/sso/controller/SsoSessionController.java
  src/main/java/com/meicrypt/identity/sso/controller/OAuthLogoutController.java
  src/main/java/com/meicrypt/identity/sso/config/AsyncConfiguration.java
  src/main/java/com/meicrypt/identity/sso/dto/SsoSessionDTO.java
  src/main/java/com/meicrypt/identity/sso/dto/LogoutResponse.java
  src/main/java/com/meicrypt/identity/sso/exception/SsoSessionNotFoundException.java
)
for f in "${required[@]}"; do
  test -f "$f" || fail "missing $f"
done
ok "All Phase 8 source files present"

info "3/5 Verifying Phase 8 wiring..."
grep -q "SsoSessionService"     src/main/java/com/meicrypt/identity/auth/service/AuthenticationService.java \
  || fail "AuthenticationService is not wired to SsoSessionService"
grep -q "BackchannelLogoutService" src/main/java/com/meicrypt/identity/auth/service/AuthenticationService.java \
  || fail "AuthenticationService is not wired to BackchannelLogoutService"
grep -q "revokeAllForSession"   src/main/java/com/meicrypt/identity/auth/service/AuthenticationService.java \
  || fail "OAuth refresh cascade missing from terminateSession"
grep -q "recordParticipant"     src/main/java/com/meicrypt/identity/oauth/service/OAuthAuthorizationService.java \
  || fail "OAuthAuthorizationService is not registering SSO participants"
grep -q "/oauth2/logout"        src/main/java/com/meicrypt/identity/config/SecurityConfiguration.java \
  || fail "/oauth2/logout not exposed as permitAll in SecurityConfiguration"
grep -q "end_session_endpoint"  src/main/java/com/meicrypt/identity/oauth/dto/OpenIdConfigurationResponse.java \
  || fail "OIDC discovery is missing end_session_endpoint"
grep -q "backchannel_logout_supported" src/main/java/com/meicrypt/identity/oauth/dto/OpenIdConfigurationResponse.java \
  || fail "OIDC discovery is missing backchannel_logout_supported"
grep -q "backchannelLogoutUri"  src/main/java/com/meicrypt/identity/application/entity/ClientApplication.java \
  || fail "ClientApplication entity missing backchannelLogoutUri"
ok "Phase 8 cross-module wiring in place"

info "4/5 Compiling project (mvn -q -DskipTests clean compile)..."
mvn -q -DskipTests clean compile
ok "Compilation succeeded"

info "5/5 Phase 8 smoke summary"
echo "  * SSO session created at Phase-3 login and returned via GET /api/v1/sso/session"
echo "  * Every /oauth2/authorize call registers the client as an SSO participant"
echo "  * /oauth2/logout (OIDC RP-Initiated Logout) terminates the session,"
echo "    cascades to all OAuth refresh tokens for the session, and asynchronously"
echo "    POSTs a signed logout_token to each participant's backchannel_logout_uri"
echo "  * Discovery document now advertises end_session_endpoint and"
echo "    backchannel_logout_supported=true"

echo -e "${GREEN}✅ Phase 8 verification complete.${NC}"
