# Phase 11 Verification – Structured Audit Trails

**Blueprint reference:** *MeiCrypt Identity Platform (MIP) – Phase 11, Module 11.1
Structured Audit Trails.*

Phase 11 introduces a strictly append-only audit trail capturing every
security-sensitive event (authentication, admin mutation, role change,
credential rotation, …) along with the acting principal, IP address,
User-Agent, correlation id, and free-form JSON metadata.

---

## 1. New Files

### Migration
```
src/main/resources/db/migration/
└── V12__notifications_and_audit.sql        # shared with Phase 10
```
`audit_events` schema:
* `id` UUID primary key.
* `organization_id`, `actor_user_id`, `actor_email`, `actor_type`
  (`USER|SYSTEM|CLIENT_APPLICATION|ANONYMOUS`).
* `action`, `resource_type`, `resource_id`, `status` (`SUCCESS|FAILURE`).
* `ip_address`, `user_agent`, `request_id`.
* `metadata JSONB` for structured domain-specific detail.
* Multi-column indexes: `(organization_id, occurred_at DESC)` and
  `(actor_user_id, occurred_at DESC)`.
* No `updated_at`; the table is INSERT-only.

### Java module (`com.meicrypt.identity.audit`)
```
audit/
├── controller/AuditController.java                   # /api/v1/organizations/{id}/audit/events
├── dto/AuditEventDTO.java
├── entity/{AuditActorType, AuditStatus, AuditEvent}.java
├── repository/AuditEventRepository.java
└── service/AuditService.java
```

---

## 2. Immutability Contract

* `AuditEvent` exposes only getters (plus a `Builder`); the entity has no
  setters. Combined with the pinned `updatable = false` on `occurred_at` this
  guarantees the JPA layer cannot mutate rows.
* `AuditEventRepository` exposes only read methods (`findByOrganization…`,
  `search`). Writes flow exclusively through `AuditService`.
* All writes execute in `@Transactional(propagation = REQUIRES_NEW)` so that a
  rollback in the caller's business transaction never destroys the audit
  entry (fire-and-forget from the caller's viewpoint).

---

## 3. REST Surface

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET` | `/api/v1/organizations/{organizationId}/audit/events` | JWT + `audit:log:read` | Paginated event list, optional `action`, `from`, `to` filters |

---

## 4. Usage from Domain Services

Any domain service can record events like:
```java
auditService.record(
    "USER_LOGIN_SUCCESS",
    AuditStatus.SUCCESS,
    userId,
    "USER",
    Map.of("sessionId", sessionId.toString()));
```
For pre-authenticated flows (failed login, unknown email) use:
```java
auditService.recordForActor(
    "USER_LOGIN_FAILURE",
    AuditStatus.FAILURE,
    orgId, null, email, AuditActorType.ANONYMOUS,
    "USER", email, Map.of("reason", "BAD_CREDENTIALS"));
```

---

## 5. Compile & Static Verification

```
$ mvn -q -DskipTests compile        # BUILD SUCCESS
$ ./verify-phase11.sh               # 9 passed, 0 failed
```
