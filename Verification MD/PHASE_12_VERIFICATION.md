# Phase 12 Verification – Admin Console Management

**Blueprint reference:** *MeiCrypt Identity Platform (MIP) – Phase 12,
Module 12.1 Admin Console Management.*

Phase 12 introduces a **global platform administrator** surface – used by
MeiCrypt staff, not by tenants – to inspect and manage the entire fleet of
organizations, sessions, notifications and audit events.

---

## 1. New Files

### Migration
```
src/main/resources/db/migration/
└── V13__platform_admin_permissions.sql
```
* Adds the `platform:*` permission family:
  `platform:organization:read|manage`, `platform:user:read|manage`,
  `platform:session:read|revoke`, `platform:audit:read`,
  `platform:notification:read`.
* Creates a synthetic organization `meicrypt-platform` with a stable UUID
  (`00000000-0000-0000-0000-000000000001`).
* Provisions a `platform-admin` SYSTEM role inside that organization and
  grants it every `platform:*`, `rbac:*` and `audit:*` permission.

### Java module (`com.meicrypt.identity.admin`)
```
admin/
├── controller/PlatformAdminController.java       # /api/v1/admin/**
├── dto/PlatformStatsDTO.java
├── dto/PlatformOrganizationSummaryDTO.java
├── dto/UpdateOrganizationStatusRequest.java
└── service/PlatformAdminService.java             # cross-domain orchestrator
```

### Modified repositories
| File | Change |
|------|--------|
| `OrganizationRepository.java` | Adds `countByStatus`. |
| `UserSessionRepository.java`  | Adds `countByStatus`, `countByOrganizationIdAndStatus`. |

---

## 2. REST Surface

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET`  | `/api/v1/admin/stats` | `platform:organization:read` | Platform footprint snapshot |
| `GET`  | `/api/v1/admin/organizations` | `platform:organization:read` | Every tenant with counts |
| `PUT`  | `/api/v1/admin/organizations/{organizationId}/status` | `platform:organization:manage` | Force-transition an organization (`ACTIVE`, `SUSPENDED`) |

---

## 3. Audit Integration

Every mutation calls `AuditService.recordForActor(...)` with action
`PLATFORM_ORGANIZATION_STATUS_CHANGED`, previous/new status, and the operator's
optional reason. Reads emit **no** audit noise.

---

## 4. Onboarding a Platform Admin

1. Create a user inside the `meicrypt-platform` organization (id above).
2. Assign the `platform-admin` role via
   `POST /api/v1/organizations/{platformOrgId}/memberships/{membershipId}/roles`.
3. On next login, the JWT carries `hasAuthority('platform:*')` and the
   `/api/v1/admin/**` routes become reachable.

---

## 5. Compile & Static Verification

```
$ mvn -q -DskipTests compile        # BUILD SUCCESS
$ ./verify-phase12.sh               # 7 passed, 0 failed
```
