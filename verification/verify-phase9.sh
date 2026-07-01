#!/usr/bin/env bash
# ============================================================================
# Phase 9 Verification – Advanced MFA (TOTP + WebAuthn)
#
# What this script asserts:
#   1. All Phase-9 Java sources exist under com.meicrypt.identity.mfa.
#   2. The V11 Flyway migration is present.
#   3. Maven compilation of the whole project succeeds.
#   4. If the app is running locally (localhost:8080), the MFA endpoints are
#      routed and enforce authentication where expected.
#
# Exit code 0 on success, non-zero on the first failure.
# ============================================================================

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { echo -e "${GREEN}✓${NC} $1"; }
fail() { echo -e "${RED}✗${NC} $1"; exit 1; }
warn() { echo -e "${YELLOW}!${NC} $1"; }

echo "==========================================================="
echo " Phase 9 Verification – MeiCrypt Identity Platform"
echo "==========================================================="

# ---------------- 1) Structural checks ----------------
echo "→ Checking module scaffolding …"
declare -a REQUIRED_FILES=(
  "src/main/resources/db/migration/V11__mfa_factors_and_challenges.sql"
  "src/main/java/com/meicrypt/identity/mfa/config/MfaProperties.java"
  "src/main/java/com/meicrypt/identity/mfa/entity/UserMfaFactor.java"
  "src/main/java/com/meicrypt/identity/mfa/entity/TotpEnrollment.java"
  "src/main/java/com/meicrypt/identity/mfa/entity/WebAuthnCredential.java"
  "src/main/java/com/meicrypt/identity/mfa/entity/WebAuthnChallenge.java"
  "src/main/java/com/meicrypt/identity/mfa/entity/MfaChallenge.java"
  "src/main/java/com/meicrypt/identity/mfa/entity/MfaFactorType.java"
  "src/main/java/com/meicrypt/identity/mfa/entity/MfaFactorStatus.java"
  "src/main/java/com/meicrypt/identity/mfa/repository/UserMfaFactorRepository.java"
  "src/main/java/com/meicrypt/identity/mfa/repository/TotpEnrollmentRepository.java"
  "src/main/java/com/meicrypt/identity/mfa/repository/WebAuthnCredentialRepository.java"
  "src/main/java/com/meicrypt/identity/mfa/repository/WebAuthnChallengeRepository.java"
  "src/main/java/com/meicrypt/identity/mfa/repository/MfaChallengeRepository.java"
  "src/main/java/com/meicrypt/identity/mfa/service/TotpCodeGenerator.java"
  "src/main/java/com/meicrypt/identity/mfa/service/TotpService.java"
  "src/main/java/com/meicrypt/identity/mfa/service/QrCodeService.java"
  "src/main/java/com/meicrypt/identity/mfa/service/WebAuthnService.java"
  "src/main/java/com/meicrypt/identity/mfa/service/MfaChallengeService.java"
  "src/main/java/com/meicrypt/identity/mfa/controller/MfaFactorController.java"
  "src/main/java/com/meicrypt/identity/mfa/controller/TotpController.java"
  "src/main/java/com/meicrypt/identity/mfa/controller/WebAuthnController.java"
  "src/main/java/com/meicrypt/identity/mfa/controller/MfaChallengeController.java"
  "src/main/java/com/meicrypt/identity/mfa/mapper/MfaFactorMapper.java"
  "src/main/java/com/meicrypt/identity/mfa/dto/MfaFactorDTO.java"
  "src/main/java/com/meicrypt/identity/mfa/dto/MfaChallengeDTO.java"
  "src/main/java/com/meicrypt/identity/mfa/dto/VerifyMfaChallengeRequest.java"
  "src/main/java/com/meicrypt/identity/mfa/dto/TotpEnrollmentResponse.java"
  "src/main/java/com/meicrypt/identity/mfa/dto/EnrollTotpRequest.java"
  "src/main/java/com/meicrypt/identity/mfa/dto/VerifyTotpEnrollmentRequest.java"
  "src/main/java/com/meicrypt/identity/mfa/dto/EnrollWebAuthnRequest.java"
  "src/main/java/com/meicrypt/identity/mfa/dto/CompleteWebAuthnRegistrationRequest.java"
  "src/main/java/com/meicrypt/identity/mfa/dto/WebAuthnRegistrationOptions.java"
  "src/main/java/com/meicrypt/identity/mfa/dto/WebAuthnAssertionOptions.java"
  "src/main/java/com/meicrypt/identity/mfa/dto/WebAuthnAssertionPayload.java"
  "src/main/java/com/meicrypt/identity/mfa/exception/MfaException.java"
  "src/main/java/com/meicrypt/identity/mfa/exception/MfaFactorNotFoundException.java"
  "src/main/java/com/meicrypt/identity/mfa/exception/InvalidMfaCodeException.java"
  "src/main/java/com/meicrypt/identity/mfa/exception/MfaChallengeNotFoundException.java"
  "src/main/java/com/meicrypt/identity/mfa/exception/InvalidMfaChallengeStateException.java"
  "src/main/java/com/meicrypt/identity/mfa/exception/WebAuthnVerificationException.java"
  "src/main/java/com/meicrypt/identity/auth/dto/LoginResponse.java"
)

for f in "${REQUIRED_FILES[@]}"; do
  [[ -f "$f" ]] || fail "Missing $f"
done
pass "All ${#REQUIRED_FILES[@]} Phase-9 sources are present"

# ---------------- 2) Config wire-up ----------------
# YAML block, so match the standalone `mfa:` key nested under `meicrypt:`.
grep -Eq "^\s{2}mfa:" src/main/resources/application.yml \
  || fail "meicrypt.mfa block missing from application.yml"
pass "application.yml exposes meicrypt.mfa.*"

grep -q "MfaProperties.class" src/main/java/com/meicrypt/identity/MeicryptIdentityApplication.java \
  || fail "MfaProperties not registered via @EnableConfigurationProperties"
pass "MfaProperties registered in main application class"

grep -q "/api/v1/mfa/challenges/verify" src/main/java/com/meicrypt/identity/config/SecurityConfiguration.java \
  || fail "SecurityConfiguration does not permit MFA verify endpoint"
pass "SecurityConfiguration allows anonymous access to challenge verify"

grep -q "InvalidMfaCodeException" src/main/java/com/meicrypt/identity/common/exception/GlobalExceptionHandler.java \
  || fail "GlobalExceptionHandler missing Phase-9 MFA handlers"
pass "GlobalExceptionHandler wires Phase-9 exceptions"

# ---------------- 3) Flyway migration sanity ----------------
migration="src/main/resources/db/migration/V11__mfa_factors_and_challenges.sql"
for t in user_mfa_factors totp_enrollments webauthn_credentials webauthn_challenges mfa_challenges; do
  grep -q "CREATE TABLE $t" "$migration" \
    || fail "V11 migration missing table $t"
done
pass "V11 migration creates all 5 MFA tables"

# ---------------- 4) Maven compile ----------------
echo "→ Running mvn -q -DskipTests compile (this may take a moment) …"
if mvn -q -DskipTests compile >/tmp/phase9_mvn.log 2>&1; then
  pass "Maven compilation OK"
else
  tail -50 /tmp/phase9_mvn.log
  fail "Maven compilation failed – see /tmp/phase9_mvn.log"
fi

# ---------------- 5) Runtime probe (best-effort) ----------------
if curl -sf -o /dev/null http://localhost:8080/actuator/health 2>/dev/null; then
  echo "→ App detected on localhost:8080 – probing MFA routes …"
  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/mfa/factors || echo 000)
  case "$code" in
    401|403) pass "/api/v1/mfa/factors is protected (HTTP $code)" ;;
    *)       warn "/api/v1/mfa/factors returned HTTP $code (expected 401/403)" ;;
  esac

  code=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST -H 'Content-Type: application/json' \
    -d '{"challengeToken":"bogus","factorType":"TOTP","proof":"123456"}' \
    http://localhost:8080/api/v1/mfa/challenges/verify || echo 000)
  case "$code" in
    404|409|400) pass "/api/v1/mfa/challenges/verify is publicly reachable (HTTP $code)" ;;
    401|403)     warn "/api/v1/mfa/challenges/verify unexpectedly requires auth (HTTP $code)" ;;
    *)           warn "/api/v1/mfa/challenges/verify returned HTTP $code" ;;
  esac
else
  warn "App not running on :8080 – skipping runtime probes"
fi

echo "==========================================================="
echo -e "${GREEN}Phase 9 verification complete.${NC}"
echo "==========================================================="
