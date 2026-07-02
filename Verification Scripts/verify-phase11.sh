#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Phase 11 - Structured Audit Trails: static verification.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
AUDIT_ROOT="$ROOT/src/main/java/com/meicrypt/identity/audit"

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

echo "── Phase 11: Structured Audit Trails ──"
check "Migration V12 (also creates audit_events)" \
      "$ROOT/src/main/resources/db/migration/V12__notifications_and_audit.sql"
check "AuditActorType enum"      "$AUDIT_ROOT/entity/AuditActorType.java"
check "AuditStatus enum"         "$AUDIT_ROOT/entity/AuditStatus.java"
check "AuditEvent entity (immutable)" "$AUDIT_ROOT/entity/AuditEvent.java"
check "AuditEventRepository"     "$AUDIT_ROOT/repository/AuditEventRepository.java"
check "AuditEventDTO"            "$AUDIT_ROOT/dto/AuditEventDTO.java"
check "AuditService (REQUIRES_NEW)" "$AUDIT_ROOT/service/AuditService.java"
check "AuditController"          "$AUDIT_ROOT/controller/AuditController.java"

if grep -q "audit_events"        "$ROOT/src/main/resources/db/migration/V12__notifications_and_audit.sql"; then
    printf "  ✔ Migration V12 declares audit_events\n"
    pass=$((pass + 1))
else
    printf "  ✘ Migration V12 does not declare audit_events\n"
    fail=$((fail + 1))
fi

echo
echo "Phase 11 checks: ${pass} passed, ${fail} failed"
exit "$fail"
