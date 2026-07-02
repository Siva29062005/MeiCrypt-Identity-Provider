#!/bin/bash

echo "=== Phase 4 Verification Script ==="
echo ""

PG="docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity"

echo "1. Checking Phase 4 database tables..."
TABLES=$($PG -t -A -c "SELECT COUNT(*)
                         FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('permissions','roles','role_permissions','membership_role_assignments');")
TABLES=$(echo "$TABLES" | tr -d '[:space:]')
if [ "$TABLES" = "4" ]; then
    echo "✅ All 4 Phase 4 tables exist (permissions, roles, role_permissions, membership_role_assignments)"
else
    echo "❌ Missing Phase 4 tables (found $TABLES/4)"
fi
echo ""

echo "2. Checking Flyway migrations V5 and V6..."
MIGRATIONS=$($PG -t -A -c "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('5','6') AND success = true;")
MIGRATIONS=$(echo "$MIGRATIONS" | tr -d '[:space:]')
if [ "$MIGRATIONS" = "2" ]; then
    echo "✅ Migrations V5 and V6 applied successfully"
else
    echo "❌ Missing Phase 4 migrations (found $MIGRATIONS/2 successful)"
fi
echo ""

echo "3. Checking permission catalog seed..."
PERMS=$($PG -t -A -c "SELECT COUNT(*) FROM permissions;")
PERMS=$(echo "$PERMS" | tr -d '[:space:]')
if [ "$PERMS" -ge "23" ]; then
    echo "✅ Permission catalog seeded ($PERMS permissions)"
else
    echo "❌ Permission catalog underseeded (found $PERMS, expected >= 23)"
fi
echo ""

echo "4. Checking SYSTEM roles per organization..."
ORGS=$($PG -t -A -c "SELECT COUNT(*) FROM organizations;")
ORGS=$(echo "$ORGS" | tr -d '[:space:]')
SYS_ROLES=$($PG -t -A -c "SELECT COUNT(*) FROM roles WHERE role_type='SYSTEM' AND slug IN ('owner','admin','member');")
SYS_ROLES=$(echo "$SYS_ROLES" | tr -d '[:space:]')
EXPECTED=$((ORGS * 3))
if [ "$ORGS" -gt "0" ] && [ "$SYS_ROLES" = "$EXPECTED" ]; then
    echo "✅ Every organization has OWNER / ADMIN / MEMBER SYSTEM roles ($SYS_ROLES total for $ORGS orgs)"
else
    echo "⚠️  SYSTEM role coverage: $SYS_ROLES rows across $ORGS orgs (expected $EXPECTED)"
fi
echo ""

echo "5. Checking default role backfill for active memberships..."
ACTIVE=$($PG -t -A -c "SELECT COUNT(*) FROM organization_memberships WHERE status='ACTIVE';")
ACTIVE=$(echo "$ACTIVE" | tr -d '[:space:]')
COVERED=$($PG -t -A -c "SELECT COUNT(DISTINCT m.id)
                         FROM organization_memberships m
                         JOIN membership_role_assignments mra ON mra.membership_id = m.id
                        WHERE m.status='ACTIVE';")
COVERED=$(echo "$COVERED" | tr -d '[:space:]')
if [ "$ACTIVE" = "0" ] || [ "$COVERED" = "$ACTIVE" ]; then
    echo "✅ All $ACTIVE active memberships have at least one role assignment"
else
    echo "⚠️  Only $COVERED/$ACTIVE active memberships have assignments (backfill may not have run)"
fi
echo ""

echo "6. Compiling project..."
mvn clean compile -q > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Project compiles successfully"
else
    echo "❌ Compilation failed"
fi
echo ""

echo "7. Probing RBAC endpoints (auth required, expects HTTP 401 or 403 without token)..."
STATUS=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/v1/rbac/permissions 2>/dev/null)
case "$STATUS" in
    401|403) echo "✅ /api/v1/rbac/permissions correctly denies anonymous access (HTTP $STATUS)" ;;
    200)     echo "⚠️  /api/v1/rbac/permissions returned 200 without a token — security may be misconfigured" ;;
    000|"")  echo "ℹ️  App not reachable on :8080 — start it with 'mvn spring-boot:run' to run this check" ;;
    *)       echo "⚠️  Unexpected status $STATUS from /api/v1/rbac/permissions" ;;
esac
echo ""

echo "=== Verification Complete ==="
echo ""
echo "Next steps:"
echo "1. Run: mvn spring-boot:run"
echo "2. Visit: http://localhost:8080/swagger-ui.html"
echo "3. Follow the API scenarios in PHASE_4_VERIFICATION.md"
echo "   - Module 4.1: list permissions"
echo "   - Module 4.2: role CRUD (create/update/delete)"
echo "   - Module 4.3: assign / revoke roles"
echo "   - Module 4.4: @PreAuthorize checks (403 for non-admins, 403 cross-tenant)"
