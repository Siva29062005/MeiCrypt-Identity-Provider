# Phase 1 Verification Guide

This guide helps you verify that Phase 1 (Organization Engine) is correctly implemented and operational.

## ✅ Phase 1 Modules Implemented

- **Module 1.1**: Organization Entity Model ✓
- **Module 1.2**: Settings Matrix ✓
- **Module 1.3**: Membership Management ✓
- **Module 1.4**: Invitation Subsystem ✓
- **Module 1.5**: Organization Custom Domains ✓

---

## 📋 Pre-Verification Checklist

### 1. Ensure Infrastructure is Running

```bash
docker-compose ps
```

Expected: All containers (postgres, redis, pgadmin, redis-commander) should be `Up` and `healthy`.

### 2. Verify Database Schema

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "\dt"
```

Expected tables:
- `organizations`
- `organization_settings`
- `organization_memberships` ← **New in Phase 1**
- `organization_invitations` ← **New in Phase 1**
- `organization_custom_domains` ← **New in Phase 1**
- `audit_logs`
- `flyway_schema_history`

### 3. Check Migration Status

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;"
```

Expected:
- V1: `initial_schema`
- V2: `organization membership and invitations`

### 4. Start the Application

```bash
mvn spring-boot:run
```

Expected: Application starts on port 8080 without errors.

---

## 🧪 Phase 1 API Verification

Once the application is running, test the new Phase 1 endpoints:

### Module 1.3: Membership Management

#### 1. Create a Membership

```bash
curl -X POST http://localhost:8080/api/v1/organizations/memberships \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "00000000-0000-0000-0000-000000000001",
    "userId": "11111111-1111-1111-1111-111111111111",
    "role": "MEMBER"
  }'
```

Expected: HTTP 201 with membership details including id, role, status (ACTIVE), and joinedAt.

#### 2. Get Memberships for Organization

```bash
curl http://localhost:8080/api/v1/organizations/memberships/organization/00000000-0000-0000-0000-000000000001
```

Expected: HTTP 200 with array of memberships (including the one just created).

#### 3. Get Membership by ID

```bash
# Replace {membershipId} with actual ID from create response
curl http://localhost:8080/api/v1/organizations/memberships/{membershipId}
```

Expected: HTTP 200 with membership details.

#### 4. Update Membership

```bash
curl -X PUT http://localhost:8080/api/v1/organizations/memberships/{membershipId} \
  -H "Content-Type: application/json" \
  -d '{
    "role": "ADMIN",
    "status": "ACTIVE"
  }'
```

Expected: HTTP 200 with updated membership (role changed to ADMIN).

#### 5. Count Active Memberships

```bash
curl http://localhost:8080/api/v1/organizations/memberships/organization/00000000-0000-0000-0000-000000000001/count
```

Expected: HTTP 200 with count number.

---

### Module 1.4: Invitation Subsystem

#### 1. Create an Invitation

```bash
curl -X POST http://localhost:8080/api/v1/organizations/invitations \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "00000000-0000-0000-0000-000000000001",
    "email": "newuser@example.com",
    "role": "MEMBER"
  }'
```

Expected: HTTP 201 with invitation details including id, email, status (PENDING), and expiresAt (7 days from now).

#### 2. Get Invitations for Organization

```bash
curl http://localhost:8080/api/v1/organizations/invitations/organization/00000000-0000-0000-0000-000000000001
```

Expected: HTTP 200 with array of invitations.

#### 3. Get Pending Invitations

```bash
curl http://localhost:8080/api/v1/organizations/invitations/organization/00000000-0000-0000-0000-000000000001/pending
```

Expected: HTTP 200 with array of pending invitations only.

#### 4. Accept an Invitation

First, you need to create an invitation and get its token from the database:

```bash
# Get invitation token
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "SELECT invitation_token FROM organization_invitations WHERE email='newuser@example.com' AND status='PENDING' LIMIT 1;"
```

Then accept it:

```bash
curl -X POST http://localhost:8080/api/v1/organizations/invitations/accept \
  -H "Content-Type: application/json" \
  -d '{
    "invitationToken": "PASTE_TOKEN_HERE",
    "userId": "22222222-2222-2222-2222-222222222222"
  }'
```

Expected: HTTP 200 with updated invitation (status changed to ACCEPTED, acceptedAt set).

#### 5. Revoke an Invitation

```bash
curl -X DELETE http://localhost:8080/api/v1/organizations/invitations/{invitationId}
```

Expected: HTTP 204 (no content).

---

### Module 1.5: Organization Custom Domains

#### 1. Create a Custom Domain

```bash
curl -X POST http://localhost:8080/api/v1/organizations/domains \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "00000000-0000-0000-0000-000000000001",
    "domain": "example.com",
    "verificationMethod": "DNS_TXT"
  }'
```

Expected: HTTP 201 with domain details including id, domain, verificationStatus (PENDING), and verificationMethod.

#### 2. Get Verification Info

```bash
curl http://localhost:8080/api/v1/organizations/domains/{domainId}/verification-info
```

Expected: HTTP 200 with verification instructions including the token and method-specific steps.

#### 3. Verify Domain

```bash
curl -X POST http://localhost:8080/api/v1/organizations/domains/{domainId}/verify
```

Expected: HTTP 200 with updated domain (verificationStatus changed to VERIFIED, verifiedAt set).

#### 4. Set Domain as Primary

```bash
curl -X PUT http://localhost:8080/api/v1/organizations/domains/{domainId}/set-primary
```

Expected: HTTP 200 with updated domain (isPrimary set to true).

#### 5. Get All Domains for Organization

```bash
curl http://localhost:8080/api/v1/organizations/domains/organization/00000000-0000-0000-0000-000000000001
```

Expected: HTTP 200 with array of custom domains.

#### 6. Get Verified Domains Only

```bash
curl http://localhost:8080/api/v1/organizations/domains/organization/00000000-0000-0000-0000-000000000001/verified
```

Expected: HTTP 200 with array of verified domains only.

---

## 🔍 Database Verification

### Check Membership Data

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "
SELECT id, organization_id, user_id, role, status, joined_at 
FROM organization_memberships 
ORDER BY created_at DESC 
LIMIT 5;"
```

Expected: Shows created memberships with all fields populated.

### Check Invitation Data

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "
SELECT id, organization_id, email, role, status, expires_at, accepted_at 
FROM organization_invitations 
ORDER BY created_at DESC 
LIMIT 5;"
```

Expected: Shows created invitations with proper status transitions.

### Check Custom Domain Data

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "
SELECT id, organization_id, domain, verification_status, verification_method, is_primary 
FROM organization_custom_domains 
ORDER BY created_at DESC 
LIMIT 5;"
```

Expected: Shows custom domains with verification status.

---

## 📊 OpenAPI Documentation Verification

1. Open Swagger UI: http://localhost:8080/swagger-ui.html

2. Verify new endpoint groups are visible:
   - **Organization Memberships** - 9 endpoints
   - **Organization Invitations** - 7 endpoints
   - **Organization Custom Domains** - 8 endpoints

3. Test endpoints directly from Swagger UI using the "Try it out" feature.

---

## ✅ Success Criteria

Phase 1 is successfully verified when:

- [x] All 7 database tables exist (including 3 new Phase 1 tables)
- [x] Flyway shows V2 migration completed
- [x] Application compiles without errors (`mvn clean compile`)
- [x] Application starts successfully on port 8080
- [x] Swagger UI displays all new Phase 1 endpoint groups
- [x] **Membership Management**: Can create, read, update, delete memberships
- [x] **Invitation System**: Can create, accept, revoke invitations
- [x] **Custom Domains**: Can add domains, get verification info, verify domains
- [x] All API endpoints return proper HTTP status codes
- [x] Database records are created correctly with all constraints
- [x] Exception handling works (try invalid requests)

---

## 🐛 Common Issues & Solutions

### Issue: Compilation errors about exception constructors

**Symptom:** Errors like "no suitable constructor found for ResourceNotFoundException"

**Solution:** Already fixed - all service classes now use correct exception constructors with proper parameters.

---

### Issue: Database foreign key constraints fail

**Symptom:** Cannot create membership/invitation with non-existent organization

**Solution:** This is expected behavior - always use valid organization IDs (like the system org: `00000000-0000-0000-0000-000000000001`)

---

### Issue: Invitation token not found

**Symptom:** Cannot accept invitation with token

**Solution:** Query the database to get the actual token:
```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "SELECT invitation_token FROM organization_invitations WHERE status='PENDING' LIMIT 1;"
```

---

## 🚀 Quick Verification Script

Create and run this script for automated verification:

```bash
#!/bin/bash

echo "=== Phase 1 Verification Script ==="
echo ""

echo "1. Checking database tables..."
TABLES=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('organization_memberships', 'organization_invitations', 'organization_custom_domains');")
if [ "$TABLES" -eq 3 ]; then
    echo "✅ All 3 Phase 1 tables exist"
else
    echo "❌ Missing Phase 1 tables (found $TABLES/3)"
fi
echo ""

echo "2. Checking Flyway migrations..."
MIGRATIONS=$(docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -t -c "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2');")
if [ "$MIGRATIONS" -eq 2 ]; then
    echo "✅ Both migrations (V1 and V2) applied"
else
    echo "❌ Missing migrations (found $MIGRATIONS/2)"
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
echo "3. Test Phase 1 endpoints using the examples above"
```

Save as `verify-phase1.sh` and run:
```bash
chmod +x verify-phase1.sh
./verify-phase1.sh
```

---

**If all checks pass, Phase 1 is complete and ready for Phase 2! 🎉**
