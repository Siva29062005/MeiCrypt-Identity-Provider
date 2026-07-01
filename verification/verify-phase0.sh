#!/bin/bash

echo "=== Phase 0 Verification Script ==="
echo ""

echo "1. Checking Docker containers..."
docker-compose ps
echo ""

echo "2. Testing PostgreSQL connection..."
docker exec -it meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "SELECT version();" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ PostgreSQL is running"
else
    echo "❌ PostgreSQL connection failed"
fi
echo ""

echo "3. Testing Redis connection..."
docker exec -it meicrypt-redis redis-cli ping > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Redis is running"
else
    echo "❌ Redis connection failed"
fi
echo ""

echo "4. Checking database tables..."
docker exec -it meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "\dt" 2>&1 | grep -q "organizations"
if [ $? -eq 0 ]; then
    echo "✅ Database schema exists"
else
    echo "❌ Database schema not found - run: mvn flyway:migrate"
fi
echo ""

echo "5. Building application..."
mvn clean compile -q
if [ $? -eq 0 ]; then
    echo "✅ Application compiles successfully"
else
    echo "❌ Compilation failed"
fi
echo ""

echo "=== Verification Complete ==="
echo ""
echo "Next steps:"
echo "1. Run: mvn spring-boot:run"
echo "2. Visit: http://localhost:8080/swagger-ui.html"
echo "3. Check: curl http://localhost:8080/actuator/health"
