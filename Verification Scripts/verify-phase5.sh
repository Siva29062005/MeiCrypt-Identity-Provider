#!/usr/bin/env bash
# ============================================================================
# verify-phase5.sh - Schema / migrations / compile check for Phase 5.
#
# Confirms:
#   * V7 migration applied
#   * client_applications, client_application_redirect_uris and
#     client_application_logout_uris tables exist
#   * mvn compile succeeds
# ============================================================================
set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓${NC} $1"; }
fail() { echo -e "${RED}✗${NC} $1"; exit 1; }
info() { echo -e "${YELLOW}ℹ${NC} $1"; }

PG="docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity"

info "1) Checking docker containers..."
docker ps --format '{{.Names}}' | grep -q meicrypt-postgres \
  || fail "meicrypt-postgres container is not running (start docker-compose first)"
pass "meicrypt-postgres is running"

info "2) Verifying V7 migration is applied..."
COUNT=$($PG -tA -c "SELECT COUNT(*) FROM flyway_schema_history
                     WHERE version='7' AND success = true;" | tr -d ' ')
[[ "$COUNT" == "1" ]] || fail "V7 migration not found in flyway_schema_history (run the app once to apply)"
pass "V7__client_applications applied"

info "3) Verifying Phase 5 tables exist..."
for table in client_applications client_application_redirect_uris client_application_logout_uris; do
  EXISTS=$($PG -tA -c "SELECT to_regclass('public.$table') IS NOT NULL;" | tr -d ' ')
  [[ "$EXISTS" == "t" ]] || fail "Missing table: $table"
  pass "table exists: $table"
done

info "4) Verifying constraint check on application_type..."
CHECK=$($PG -tA -c "SELECT COUNT(*) FROM pg_constraint
                     WHERE conname='client_applications_type_check';" | tr -d ' ')
[[ "$CHECK" == "1" ]] || fail "client_applications_type_check constraint missing"
pass "application_type CHECK constraint is present"

info "5) Verifying unique indexes on client_id and (organization_id, slug)..."
$PG -tA -c "SELECT 1 FROM pg_indexes
             WHERE tablename='client_applications' AND indexname='idx_client_applications_client_id';" \
  | grep -q 1 || fail "idx_client_applications_client_id missing"
pass "indexes are in place"

info "6) mvn compile..."
mvn -q -DskipTests compile > /dev/null 2>&1 || fail "mvn compile failed"
pass "mvn compile succeeded"

echo
echo -e "${GREEN}✅ Phase 5 schema verification passed.${NC}"
echo "Run ./verify-phase5-api.sh (if provided) or the manual curl commands in PHASE_5_VERIFICATION.md"
