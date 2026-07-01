# Phase 10 Verification – Notification Delivery

**Blueprint reference:** *MeiCrypt Identity Platform (MIP) – Phase 10, Module 10.1
Notification Delivery.*

Phase 10 introduces an asynchronous outbox-style notification pipeline that
decouples business flows (verification, invitation, password reset, …) from
external mail / SMS gateways.

---

## 1. New Files

### Migration
```
src/main/resources/db/migration/
└── V12__notifications_and_audit.sql        # (also seeds Phase 11 audit_events)
```
Creates:
* `notification_templates` – reusable Handlebars templates keyed by
  `(templateKey, channel, locale)`.
* `notifications`          – durable outbox (PENDING → SENDING → SENT / FAILED).
* Three seeded templates (`user.verification.email`,
  `user.password.reset.email`, `organization.invitation.email`).

### Java module (`com.meicrypt.identity.notification`)
```
notification/
├── config/NotificationProperties.java          # meicrypt.notifications.*
├── controller/NotificationController.java      # /api/v1/organizations/{id}/notifications
├── dto/{NotificationDTO, SendNotificationRequest}.java
├── entity/{NotificationChannel, NotificationStatus,
│           NotificationTemplate, Notification}.java
├── exception/NotificationTemplateNotFoundException.java
├── repository/{NotificationRepository, NotificationTemplateRepository}.java
└── service/
    ├── NotificationRenderer.java               # {{placeholder}} substitution
    ├── NotificationTransport.java              # SPI
    ├── LoggingNotificationTransport.java       # default log-only transport
    ├── NotificationService.java                # enqueue API
    └── NotificationDispatcher.java             # @Scheduled outbox worker
```

### Modified files
| File | Purpose |
|------|---------|
| `MeicryptIdentityApplication.java` | Adds `@EnableScheduling`, `@EnableAsync`, and binds `NotificationProperties`. |
| `application.yml` | Adds the `meicrypt.notifications.*` config block. |
| `GlobalExceptionHandler.java` | Adds RFC 7807 handler for `NotificationTemplateNotFoundException`. |

---

## 2. REST Surface

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/v1/organizations/{organizationId}/notifications` | JWT + `audit:log:read` | Enqueue a notification (returns `202 Accepted`) |
| `GET`  | `/api/v1/organizations/{organizationId}/notifications` | JWT + `audit:log:read` | List recent outbox rows for admins |

---

## 3. Runtime Contract

* Callers **always** hit `NotificationService.enqueue(...)`, which persists
  a row and returns immediately. No SMTP / SMS I/O happens on the request thread.
* `NotificationDispatcher` polls every second (`meicrypt.notifications.poll-interval-ms`),
  picks up at most `batch-size` PENDING rows, and delegates delivery to the
  first `NotificationTransport` whose `supports(...)` returns `true`.
* The default `LoggingNotificationTransport` writes a structured log line
  instead of talking to a real gateway, so local dev works out of the box.
* Failed deliveries increment `attempt_count`, back off (5s × attempts), and
  transition to `FAILED` after `max-attempts` (default 5).

---

## 4. Configuration Keys

```yaml
meicrypt:
  notifications:
    batch-size: 20
    max-attempts: 5
    default-locale: en
    worker-enabled: true
    log-only-transport: true
    poll-interval-ms: 1000
```

---

## 5. Compile & Static Verification

```
$ mvn -q -DskipTests compile        # BUILD SUCCESS
$ ./verify-phase10.sh               # 19 passed, 0 failed
```
