# Phase 13 Verification – Developer Portal Dashboard

**Blueprint reference:** *MeiCrypt Identity Platform (MIP) – Phase 13,
Module 13.1 Developer Portal Dashboard.*

Phase 13 exposes a **self-service** surface for engineering teams inside a
tenant organization to manage the OAuth client applications they own —
independently of the org-scoped admin console.

---

## 1. New Files

### Migration
Reuses `V13__platform_admin_permissions.sql` (see Phase 12 doc), which also:
* Adds `developer:application:read`, `developer:application:manage`,
  `developer:application:rotate_secret` permissions.
* Grants **all three** to every existing OWNER role.
* Grants **read + manage (but NOT rotate_secret)** to every existing ADMIN
  role – rotation stays with the OWNER to prevent lateral privilege escalation.

### Java module (`com.meicrypt.identity.developer`)
```
developer/
└── controller/DeveloperPortalController.java     # /api/v1/developer/applications/**
```

The controller is intentionally thin: every mutation delegates to the Phase 5
`ClientApplicationService`. Multi-tenant safety is preserved because the
authenticated principal (`AuthenticatedUser.organizationId()`) is used to scope
each call rather than trusting a path variable.

---

## 2. REST Surface

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET`    | `/api/v1/developer/applications`                     | `developer:application:read`          | List my org's client apps |
| `GET`    | `/api/v1/developer/applications/{applicationId}`     | `developer:application:read`          | Fetch a single app |
| `POST`   | `/api/v1/developer/applications`                     | `developer:application:manage`        | Register a new client app (one-shot secret disclosure) |
| `PUT`    | `/api/v1/developer/applications/{applicationId}`     | `developer:application:manage`        | Update name, redirect URIs, back-channel logout, TTLs |
| `DELETE` | `/api/v1/developer/applications/{applicationId}`     | `developer:application:manage`        | Delete an application |
| `POST`   | `/api/v1/developer/applications/{applicationId}/secret/rotate` | `developer:application:rotate_secret` | Rotate `client_secret` |

Existing org-scoped routes at
`/api/v1/organizations/{organizationId}/applications` remain in place for
administrators and are guarded by `oauth:application:*` permissions.

---

## 3. Rotation Schedule

Rotation is initiated by the developer via the `secret/rotate` route. The
service atomically:
1. Generates a fresh 256-bit `client_secret` (Phase 5 `ClientCredentialGenerator`).
2. Persists a BCrypt hash and updates `client_secret_last_rotated_at`.
3. Returns the plaintext secret in a **one-time** DTO – it will never be shown
   again.

Deployments still using the previous secret start receiving 401s immediately –
callers must coordinate rollout.

---

## 4. Compile & Static Verification

```
$ mvn -q -DskipTests compile        # BUILD SUCCESS
$ ./verify-phase13.sh               # 4 passed, 0 failed
```
