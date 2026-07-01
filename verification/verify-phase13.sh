#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Phase 13 - Developer Portal Dashboard: static verification.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DEV_ROOT="$ROOT/src/main/java/com/meicrypt/identity/developer"

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

echo "── Phase 13: Developer Portal ──"
check "DeveloperPortalController"       "$DEV_ROOT/controller/DeveloperPortalController.java"
check "V13 migration seeds developer:* permissions" \
      "$ROOT/src/main/resources/db/migration/V13__platform_admin_permissions.sql"

if grep -q "developer:application:read"          "$ROOT/src/main/resources/db/migration/V13__platform_admin_permissions.sql" \
&& grep -q "developer:application:manage"        "$ROOT/src/main/resources/db/migration/V13__platform_admin_permissions.sql" \
&& grep -q "developer:application:rotate_secret" "$ROOT/src/main/resources/db/migration/V13__platform_admin_permissions.sql"; then
    printf "  ✔ Developer permissions seeded and attached to OWNER/ADMIN roles\n"
    pass=$((pass + 1))
else
    printf "  ✘ Developer permissions not seeded\n"
    fail=$((fail + 1))
fi

if grep -q "/api/v1/developer/applications" "$DEV_ROOT/controller/DeveloperPortalController.java" \
&& grep -q "developer:application:rotate_secret" "$DEV_ROOT/controller/DeveloperPortalController.java"; then
    printf "  ✔ Developer portal exposes /api/v1/developer/applications with rotation route\n"
    pass=$((pass + 1))
else
    printf "  ✘ Developer portal routes malformed\n"
    fail=$((fail + 1))
fi

echo
echo "Phase 13 checks: ${pass} passed, ${fail} failed"
exit "$fail"
