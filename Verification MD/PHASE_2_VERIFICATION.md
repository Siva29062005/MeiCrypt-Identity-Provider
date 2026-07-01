# Phase 2 Verification Guide

This guide helps you verify that Phase 2 (Identity & Account Lifecycle) is correctly implemented and operational.

## ✅ Phase 2 Modules Implemented

- **Module 2.1**: User Registration System ✓
- **Module 2.2**: Verification Mechanisms ✓
- **Module 2.3**: Password Maintenance ✓
- **Module 2.4**: Profile Interface ✓
- **Module 2.5**: Lifecycle Transitions ✓

---

## 📋 Pre-Verification Checklist

### 1. Ensure Infrastructure is Running

```bash
docker-compose ps
```

Expected: All containers (postgres, redis) should be `Up` and `healthy`.

### 2. Verify Database Schema

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "\dt"
```

Expected tables:
- `organizations`
- `organization_settings`
- `organization_memberships`
- `organization_invitations`
- `organization_custom_domains`
- `users` ← **New in Phase 2**
- `verification_tokens` ← **New in Phase 2**
- `password_reset_tokens` ← **New in Phase 2**
- `audit_logs`
- `flyway_schema_history`

### 3. Check Migration Status

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;"
```

Expected:
- V1: `initial_schema`
- V2: `organization membership and invitations`
- V3: `user and verification tables` ← **New**

### 4. Start the Application

```bash
mvn spring-boot:run
```

Expected: Application starts on port 8080 without errors.

---

## 🧪 Phase 2 API Verification

Once the application is running, test the new Phase 2 endpoints:

### Module 2.1: User Registration

#### 1. Register a New User

```bash
curl -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "00000000-0000-0000-0000-000000000001",
    "email": "john.doe@example.com",
    "password": "SecurePass123!",
    "firstName": "John",
    "lastName": "Doe",
    "locale": "en",
    "timezone": "America/New_York"
  }'
```

Expected: HTTP 201 with user details. Status should be `PENDING_VERIFICATION`, `emailVerified` should be `false`.

#### 2. Check Email Availability

```bash
curl "http://localhost:8080/api/v1/users/check-email?email=john.doe@example.com&organizationId=00000000-0000-0000-0000-000000000001"
```

Expected: HTTP 200 with `{"exists": true}`.

#### 3. Get User by ID

```bash
# Replace {userId} with actual ID from registration response
curl http://localhost:8080/api/v1/users/{userId}
```

Expected: HTTP 200 with user details (password hash excluded).

---

### Module 2.2: Email Verification

#### 1. Get Verification Token from Database

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "
SELECT token, expires_at 
FROM verification_tokens 
WHERE token_type='EMAIL_VERIFICATION' AND status='PENDING' 
ORDER BY created_at DESC LIMIT 1;"
```

Copy the token value.

#### 2. Verify Email

```bash
curl -X POST http://localhost:8080/api/v1/verification/verify-email \
  -H "Content-Type: application/json" \
  -d '{
    "token": "PASTE_TOKEN_HERE"
  }'
```

Expected: HTTP 200 with `{"message": "Email verified successfully"}`.

#### 3. Check User Status After Verification

```bash
curl http://localhost:8080/api/v1/users/{userId}
```

Expected: `emailVerified` should be `true`, `status` should be `ACTIVE`.

#### 4. Resend Verification (for unverified user)

```bash
curl -X POST http://localhost:8080/api/v1/verification/resend-verification \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "00000000-0000-0000-0000-000000000001",
    "email": "john.doe@example.com"
  }'
```

Expected: HTTP 200 with success message.

---

### Module 2.3: Password Reset

#### 1. Initiate Password Reset

```bash
curl -X POST http://localhost:8080/api/v1/password-reset/initiate \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "00000000-0000-0000-0000-000000000001",
    "email": "john.doe@example.com"
  }'
```

Expected: HTTP 200 with message (same response even if email doesn't exist - security feature).

#### 2. Get Password Reset Token from Database

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "
SELECT token, expires_at 
FROM password_reset_tokens 
WHERE status='PENDING' 
ORDER BY created_at DESC LIMIT 1;"
```

Copy the token value.

#### 3. Validate Reset Token

```bash
curl "http://localhost:8080/api/v1/password-reset/validate-token?token=PASTE_TOKEN_HERE"
```

Expected: HTTP 200 with `{"valid": true}`.

#### 4. Reset Password

```bash
curl -X POST http://localhost:8080/api/v1/password-reset/reset \
  -H "Content-Type: application/json" \
  -d '{
    "token": "PASTE_TOKEN_HERE",
    "newPassword": "NewSecurePass456!"
  }'
```

Expected: HTTP 200 with `{"message": "Password reset successfully"}`.

---

### Module 2.4: Profile Management

#### 1. Update User Profile

```bash
curl -X PUT http://localhost:8080/api/v1/users/{userId}/profile \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Jonathan",
    "lastName": "Doe",
    "phoneNumber": "+1234567890",
    "timezone": "Europe/London"
  }'
```

Expected: HTTP 200 with updated user details.

#### 2. Change Password (Authenticated)

```bash
curl -X POST http://localhost:8080/api/v1/users/{userId}/change-password \
  -H "Content-Type: application/json" \
  -d '{
    "currentPassword": "NewSecurePass456!",
    "newPassword": "AnotherSecurePass789!"
  }'
```

Expected: HTTP 200 with `{"message": "Password changed successfully"}`.

---

### Module 2.5: Lifecycle Management

#### 1. Suspend User

```bash
curl -X PUT http://localhost:8080/api/v1/users/{userId}/suspend
```

Expected: HTTP 200 with user details. Status should be `SUSPENDED`.

#### 2. Activate User

```bash
curl -X PUT http://localhost:8080/api/v1/users/{userId}/activate
```

Expected: HTTP 200 with user details. Status should be `ACTIVE`.

#### 3. Deactivate User (Soft Delete)

```bash
curl -X PUT http://localhost:8080/api/v1/users/{userId}/deactivate
```

Expected: HTTP 200 with user details. Status should be `INACTIVE`.

#### 4. Get Users by Organization

```bash
curl http://localhost:8080/api/v1/users/organization/00000000-0000-0000-0000-000000000001
```

Expected: HTTP 200 with array of all users in organization.

#### 5. Get Active Users Only

```bash
curl http://localhost:8080/api/v1/users/organization/00000000-0000-0000-0000-000000000001/active
```

Expected: HTTP 200 with array of active users only.

#### 6. Count Users in Organization

```bash
curl http://localhost:8080/api/v1/users/organization/00000000-0000-0000-0000-000000000001/count
```

Expected: HTTP 200 with `{"count": <number>}`.

#### 7. Delete User Permanently (Hard Delete)

```bash
curl -X DELETE http://localhost:8080/api/v1/users/{userId}
```

Expected: HTTP 200 with `{"message": "User deleted successfully"}`.

---

## 🔍 Database Verification

### Check User Data

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "
SELECT id, email, email_verified, status, first_name, last_name, created_at 
FROM users 
ORDER BY created_at DESC 
LIMIT 5;"
```

Expected: Shows created users with all fields populated.

### Check Verification Tokens

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "
SELECT id, user_id, token_type, status, expires_at, used_at 
FROM verification_tokens 
ORDER BY created_at DESC 
LIMIT 5;"
```

Expected: Shows verification tokens with proper status transitions.

### Check Password Reset Tokens

```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "
SELECT id, user_id, status, expires_at, used_at 
FROM password_reset_tokens 
ORDER BY created_at DESC 
LIMIT 5;"
```

Expected: Shows password reset tokens with usage tracking.

---

## 📊 OpenAPI Documentation Verification

1. Open Swagger UI: http://localhost:8080/swagger-ui.html

2. Verify new endpoint groups are visible:
   - **User Management** - 12 endpoints
   - **Verification** - 2 endpoints
   - **Password Reset** - 3 endpoints

3. Test endpoints directly from Swagger UI using the "Try it out" feature.

---

## ✅ Success Criteria

Phase 2 is successfully verified when:

- [x] All 10 database tables exist (including 3 new Phase 2 tables)
- [x] Flyway shows V3 migration completed
- [x] Application compiles without errors (`mvn clean compile`)
- [x] Application starts successfully on port 8080
- [x] Swagger UI displays all new Phase 2 endpoint groups
- [x] **User Registration**: Can register users with email/password
- [x] **Email Verification**: Can verify email with tokens
- [x] **Password Reset**: Can initiate and complete password reset
- [x] **Profile Management**: Can update user profile and change password
- [x] **Lifecycle Management**: Can suspend, activate, deactivate users
- [x] All API endpoints return proper HTTP status codes
- [x] Database records are created correctly with all constraints
- [x] Exception handling works (try invalid requests)
- [x] Password validation enforces organization password policies
- [x] Account locking works after failed login attempts

---

## 🐛 Common Issues & Solutions

### Issue: Password validation fails

**Symptom:** Cannot register user with password errors

**Solution:** Check organization settings for password requirements:
```bash
docker exec meicrypt-postgres psql -U meicrypt -d meicrypt_identity -c "
SELECT password_min_length, password_require_uppercase, password_require_numbers 
FROM organization_settings 
WHERE organization_id='00000000-0000-0000-0000-000000000001';"
```

Ensure password meets all requirements (uppercase, lowercase, numbers, special chars).

---

### Issue: Email verification token expired

**Symptom:** Verification fails with "token has expired" message

**Solution:** Tokens expire after 24 hours. Request a new verification email:
```bash
curl -X POST http://localhost:8080/api/v1/verification/resend-verification \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "00000000-0000-0000-0000-000000000001",
    "email": "user@example.com"
  }'
```

---

### Issue: Cannot activate user with unverified email

**Symptom:** Activate endpoint returns "Cannot activate user with unverified email"

**Solution:** This is expected behavior. Complete email verification first, then activate.

---

## 🚀 Quick Verification Script

Create and run this script for automated verification:

```bash
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
echo "3. Test Phase 2 endpoints using the examples above"
```

Save as `verify-phase2.sh` and run:
```bash
chmod +x verify-phase2.sh
./verify-phase2.sh
```

---

**If all checks pass, Phase 2 is complete and ready for Phase 3! 🎉**
