#!/bin/bash

echo "=== Phase 2 Verification Script ==="
echo ""

echo "1. Checking database tables..."
TABLES=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('users', 'verification_tokens', 'password_reset_tokens');")
if [ "$TABLES" -eq 3 ]; then
    echo "✅ All 3 Phase 2 tables exist"
else
    echo "❌ Missing Phase 2 tables (found $TABLES/3)"
fi
echo ""

echo "2. Checking Flyway migrations..."
MIGRATIONS=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -t -c "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2', '3');")
if [ "$MIGRATIONS" -eq 3 ]; then
    echo "✅ All migrations (V1, V2, V3) applied"
else
    echo "❌ Missing migrations (found $MIGRATIONS/3)"
fi
echo ""

echo "3. Compiling project..."
mvn clean compile -q > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Project compiles successfully"
else
    echo "❌ Compilation failed"
fi
echo ""

echo "=== Verification Complete ==="
echo ""
echo "Next steps:"
echo "1. Run: mvn spring-boot:run"
echo "2. Visit: http://localhost:8080/swagger-ui.html"
echo "3. Test Phase 2 endpoints using the examples in PHASE_2_VERIFICATION.md"
