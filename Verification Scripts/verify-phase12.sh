#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Phase 12 - Platform Admin Console: static verification.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ADMIN_ROOT="$ROOT/src/main/java/com/meicrypt/identity/admin"

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

echo "── Phase 12: Platform Admin Console ──"
check "V13 migration seeds platform:* permissions" \
      "$ROOT/src/main/resources/db/migration/V13__platform_admin_permissions.sql"
check "PlatformAdminController"       "$ADMIN_ROOT/controller/PlatformAdminController.java"
check "PlatformAdminService"          "$ADMIN_ROOT/service/PlatformAdminService.java"
check "PlatformStatsDTO"              "$ADMIN_ROOT/dto/PlatformStatsDTO.java"
check "PlatformOrganizationSummaryDTO" "$ADMIN_ROOT/dto/PlatformOrganizationSummaryDTO.java"
check "UpdateOrganizationStatusRequest" "$ADMIN_ROOT/dto/UpdateOrganizationStatusRequest.java"

if grep -q "platform:organization:read"  "$ROOT/src/main/resources/db/migration/V13__platform_admin_permissions.sql" \
&& grep -q "platform:organization:manage" "$ROOT/src/main/resources/db/migration/V13__platform_admin_permissions.sql" \
&& grep -q "platform-admin"                "$ROOT/src/main/resources/db/migration/V13__platform_admin_permissions.sql"; then
    printf "  ✔ Platform role + permissions seeded in V13\n"
    pass=$((pass + 1))
else
    printf "  ✘ Platform seeds missing in V13\n"
    fail=$((fail + 1))
fi

echo
echo "Phase 12 checks: ${pass} passed, ${fail} failed"
exit "$fail"
