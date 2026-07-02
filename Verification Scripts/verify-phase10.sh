#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Phase 10 - Notification Delivery: static verification
#
# Confirms that the notification module was scaffolded, wired into the
# application context and the Flyway migration is discoverable.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
NOTIF_ROOT="$ROOT/src/main/java/com/meicrypt/identity/notification"

pass=0
fail=0
check() {
    local desc="$1"; local target="$2"
    if [[ -e "$target" ]]; then
        printf "  ✔ %s\n" "$desc"
        pass=$((pass + 1))
    else
        printf "  ✘ %s (missing: %s)\n" "$desc" "$target"
        fail=$((fail + 1))
    fi
}

echo "── Phase 10: Notification Delivery ──"
check "Flyway V12 migration"            "$ROOT/src/main/resources/db/migration/V12__notifications_and_audit.sql"
check "NotificationChannel enum"        "$NOTIF_ROOT/entity/NotificationChannel.java"
check "NotificationStatus enum"         "$NOTIF_ROOT/entity/NotificationStatus.java"
check "NotificationTemplate entity"     "$NOTIF_ROOT/entity/NotificationTemplate.java"
check "Notification entity"             "$NOTIF_ROOT/entity/Notification.java"
check "NotificationRepository"          "$NOTIF_ROOT/repository/NotificationRepository.java"
check "NotificationTemplateRepository"  "$NOTIF_ROOT/repository/NotificationTemplateRepository.java"
check "NotificationService"             "$NOTIF_ROOT/service/NotificationService.java"
check "NotificationDispatcher (worker)" "$NOTIF_ROOT/service/NotificationDispatcher.java"
check "NotificationTransport SPI"       "$NOTIF_ROOT/service/NotificationTransport.java"
check "LoggingNotificationTransport"    "$NOTIF_ROOT/service/LoggingNotificationTransport.java"
check "NotificationRenderer"            "$NOTIF_ROOT/service/NotificationRenderer.java"
check "NotificationController"          "$NOTIF_ROOT/controller/NotificationController.java"
check "SendNotificationRequest DTO"     "$NOTIF_ROOT/dto/SendNotificationRequest.java"
check "NotificationDTO"                 "$NOTIF_ROOT/dto/NotificationDTO.java"
check "NotificationProperties config"   "$NOTIF_ROOT/config/NotificationProperties.java"
check "NotificationTemplateNotFoundException" \
      "$NOTIF_ROOT/exception/NotificationTemplateNotFoundException.java"

if grep -q "meicrypt:\|meicrypt.notifications" "$ROOT/src/main/resources/application.yml" \
   && grep -q "notifications:" "$ROOT/src/main/resources/application.yml"; then
    printf "  ✔ application.yml exposes meicrypt.notifications.*\n"
    pass=$((pass + 1))
else
    printf "  ✘ application.yml missing meicrypt.notifications block\n"
    fail=$((fail + 1))
fi

if grep -q "@EnableScheduling"            "$ROOT/src/main/java/com/meicrypt/identity/MeicryptIdentityApplication.java" \
&& grep -q "NotificationProperties.class" "$ROOT/src/main/java/com/meicrypt/identity/MeicryptIdentityApplication.java"; then
    printf "  ✔ Application bootstraps scheduling + notification properties\n"
    pass=$((pass + 1))
else
    printf "  ✘ Application does not enable scheduling / properties\n"
    fail=$((fail + 1))
fi

echo
echo "Phase 10 checks: ${pass} passed, ${fail} failed"
exit "$fail"
