# Phase 0 Verification Guide

This guide helps you verify that Phase 0 (Bootstrap & Foundation Base) is correctly configured and operational.

## ✅ Verification Checklist

### 1. Infrastructure Services
Check that Docker containers are running:

```bash
docker-compose ps
```

Expected output:
- `meicrypt-postgres` - Up and healthy
- `meicrypt-redis` - Up and healthy

Verify PostgreSQL connection:
```bash
docker exec -it meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "\dt"
```

Expected: Should show `organizations`, `organization_settings`, and `audit_logs` tables.

Verify Redis connection:
```bash
docker exec -it meicrypt-redis redis-cli ping
```

Expected: `PONG`

---

### 2. Build and Compile
Compile the project to verify dependencies and code:

```bash
mvn clean compile
```

Expected: `BUILD SUCCESS`

---

### 3. Database Migrations
Run Flyway migrations to ensure database schema is created:

```bash
mvn flyway:migrate
```

Expected: Should show V1__initial_schema.sql migration successful.

Verify tables exist:
```bash
docker exec -it meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;
"
```

Expected tables:
- audit_logs
- flyway_schema_history
- organizations
- organization_settings

---

### 4. Application Startup
Start the Spring Boot application:

```bash
mvn spring-boot:run
```

Expected indicators in logs:
- ✅ `Started MeicryptIdentityApplication in X seconds`
- ✅ `Tomcat started on port 8080`
- ✅ `HikariPool-1 - Start completed`
- ✅ `Flyway migration completed successfully`
- ✅ No ERROR or WARN messages about missing beans

The application should start successfully without errors.

---

### 5. Health Check Endpoint
Check actuator health endpoint (in another terminal):

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

---

### 6. API Documentation
Verify OpenAPI/Swagger UI is accessible:

```bash
curl -I http://localhost:8080/swagger-ui.html
```

Expected: `HTTP/1.1 200` or `302 Found` (redirect)

Open in browser: http://localhost:8080/swagger-ui.html

Expected: Swagger UI with "MeiCrypt Identity Platform API" documentation.

---

### 7. Exception Handling Test
Verify global exception handler by accessing a non-existent endpoint:

```bash
curl http://localhost:8080/api/v1/nonexistent
```

Expected: JSON response with RFC 7807 Problem Details format:
```json
{
  "type": "...",
  "title": "Not Found",
  "status": 404,
  "detail": "...",
  "timestamp": "..."
}
```

---

### 8. Database Data Verification
Check that default system organization was created:

```bash
docker exec -it meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "
SELECT id, name, slug, status FROM organizations;
"
```

Expected output:
```
                  id                  | name   | slug   | status 
--------------------------------------+--------+--------+--------
 00000000-0000-0000-0000-000000000001 | System | system | ACTIVE
```

---

### 9. Logging Verification
Check that logging is working correctly:

The console should show structured logs like:
```
2024-xx-xx 12:00:00.000 [main] INFO  c.m.i.MeicryptIdentityApplication - Starting MeicryptIdentityApplication...
```

Verify log files are created (if application ran):
```bash
ls -la logs/
```

Expected: `meicrypt-identity.log` and `security.log` files.

---

### 10. Run Tests
Execute the test suite to verify testing infrastructure:

```bash
mvn test
```

Expected: 
- Testcontainers should start PostgreSQL and Redis containers
- All tests should pass
- `BUILD SUCCESS`

---

## 🔍 Common Issues & Solutions

### Issue: Port already in use
**Symptom:** Docker containers fail to start with port binding error.

**Solution:**
```bash
# Check what's using the port
lsof -i :5432  # PostgreSQL
lsof -i :6379  # Redis
lsof -i :8080  # Application

# Kill the process or change ports in docker-compose.yml
```

### Issue: Database connection refused
**Symptom:** Application fails with "Connection refused" error.

**Solution:**
```bash
# Restart PostgreSQL container
docker-compose restart postgres

# Check PostgreSQL logs
docker logs meicrypt-postgres
```

### Issue: Flyway migration fails
**Symptom:** Schema validation errors or migration failures.

**Solution:**
```bash
# Clean and rebuild database
docker-compose down -v
docker-compose up -d postgres redis
mvn flyway:migrate
```

### Issue: Redis connection timeout
**Symptom:** Redis connection timeout errors.

**Solution:**
```bash
# Restart Redis container
docker-compose restart redis

# Verify Redis is accessible
docker exec -it meicrypt-redis redis-cli ping
```

---

## 📊 Complete Verification Script

Run this all-in-one verification script:

```bash
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
```

Save this as `verify-phase0.sh`, make it executable, and run:
```bash
chmod +x verify-phase0.sh
./verify-phase0.sh
```

---

## ✅ Success Criteria

Phase 0 is successfully verified when:

- [x] All Docker containers are running and healthy
- [x] PostgreSQL database is accessible with correct schema
- [x] Redis cache is accessible
- [x] Application compiles without errors
- [x] Application starts successfully on port 8080
- [x] Health check endpoint returns UP status
- [x] Swagger UI is accessible
- [x] Exception handling returns RFC 7807 format
- [x] Default system organization exists in database
- [x] Logging is working correctly
- [x] All tests pass

---

**If all checks pass, Phase 0 is complete and ready for Phase 1! 🎉**
