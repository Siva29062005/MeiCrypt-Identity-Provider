#!/bin/bash

echo "=== Phase 1 Verification Script ==="
echo ""

echo "1. Checking database tables..."
TABLES=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('organization_memberships', 'organization_invitations', 'organization_custom_domains');" 2>/dev/null | tr -d ' ')
if [ "$TABLES" = "3" ]; then
    echo "✅ All 3 Phase 1 tables exist"
else
    echo "❌ Missing Phase 1 tables (found $TABLES/3)"
fi
echo ""

echo "2. Checking Flyway migrations..."
MIGRATIONS=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -t -c "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2');" 2>/dev/null | tr -d ' ')
if [ "$MIGRATIONS" = "2" ]; then
    echo "✅ Both migrations (V1 and V2) applied"
else
    echo "❌ Missing migrations (found $MIGRATIONS/2)"
fi
echo ""

echo "3. Listing all tables..."
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name;" 2>/dev/null
echo ""

echo "4. Compiling project..."
mvn clean compile -q > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Project compiles successfully"
else
    echo "❌ Compilation failed - run 'mvn clean compile' for details"
fi
echo ""

echo "5. Checking Phase 1 source files..."
MEMBERSHIP_FILES=$(find src/main/java/com/meicrypt/identity/organization -name "*Membership*" -type f | wc -l)
INVITATION_FILES=$(find src/main/java/com/meicrypt/identity/organization -name "*Invitation*" -type f | wc -l)
DOMAIN_FILES=$(find src/main/java/com/meicrypt/identity/organization -name "*CustomDomain*" -o -name "*Domain*" -type f | wc -l)

echo "   - Membership files: $MEMBERSHIP_FILES (expected: 7+)"
echo "   - Invitation files: $INVITATION_FILES (expected: 7+)"
echo "   - Domain files: $DOMAIN_FILES (expected: 7+)"

if [ "$MEMBERSHIP_FILES" -ge 7 ] && [ "$INVITATION_FILES" -ge 7 ] && [ "$DOMAIN_FILES" -ge 7 ]; then
    echo "✅ All Phase 1 source files present"
else
    echo "⚠️  Some source files may be missing"
fi
echo ""

echo "=== Verification Complete ==="
echo ""
echo "📋 Phase 1 Summary:"
echo "   - Module 1.3: Membership Management"
echo "   - Module 1.4: Invitation Subsystem"
echo "   - Module 1.5: Organization Custom Domains"
echo ""
echo "Next steps:"
echo "1. Run: mvn spring-boot:run"
echo "2. Visit: http://localhost:8080/swagger-ui.html"
echo "3. Test Phase 1 endpoints (see PHASE_1_VERIFICATION.md for examples)"
echo "4. Check database records with provided SQL queries"
echo ""
echo "📚 Full verification guide: PHASE_1_VERIFICATION.md"
