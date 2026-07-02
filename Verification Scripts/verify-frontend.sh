#!/usr/bin/env bash
# =============================================================================
# MeiCrypt Identity — Frontend Verification Script
# =============================================================================
# Verifies Phases F0–F12 (Bootstrap, Auth UI, Console Shell,
# Organization Mgmt, Users, RBAC, Developer Portal, OAuth Consent,
# MFA & Passkeys, Audit, Platform Admin, and Polish).
# Safe to re-run. Does NOT modify project state.
# =============================================================================

set -u
cd "$(dirname "$0")/../frontend" || {
  echo "❌ Cannot cd into frontend/ — is it created?"; exit 1;
}

RESET="\033[0m"; BOLD="\033[1m"
GREEN="\033[32m"; RED="\033[31m"; YELLOW="\033[33m"; CYAN="\033[36m"

pass=0
fail=0
warn=0

section() { echo -e "\n${BOLD}${CYAN}▸ $*${RESET}"; }
ok()      { echo -e "  ${GREEN}✓${RESET} $*"; pass=$((pass+1)); }
bad()     { echo -e "  ${RED}✗${RESET} $*"; fail=$((fail+1)); }
warn()    { echo -e "  ${YELLOW}!${RESET} $*"; warn=$((warn+1)); }

# -----------------------------------------------------------------------------
section "1. Project structure"
# -----------------------------------------------------------------------------
required_files=(
  package.json
  tsconfig.json
  next.config.ts
  postcss.config.mjs
  src/env.ts
  src/middleware.ts
  src/app/layout.tsx
  src/app/globals.css
  src/app/page.tsx
  src/app/error.tsx
  src/app/not-found.tsx
  "src/app/(auth)/layout.tsx"
  "src/app/(auth)/login/page.tsx"
  "src/app/(auth)/login/login-form.tsx"
  "src/app/(auth)/register/page.tsx"
  "src/app/(auth)/forgot-password/page.tsx"
  "src/app/(auth)/mfa-challenge/page.tsx"
  "src/app/(auth)/verify-email/page.tsx"
  "src/app/(console)/layout.tsx"
  "src/app/(console)/console/page.tsx"
  "src/app/(console)/console/users/page.tsx"
  "src/app/(console)/console/users/[userId]/page.tsx"
  "src/app/(console)/console/roles/page.tsx"
  "src/app/(console)/console/settings/page.tsx"
  "src/app/(console)/console/invitations/page.tsx"
  "src/app/(console)/console/domains/page.tsx"
  "src/app/(console)/console/applications/page.tsx"
  "src/app/(console)/console/applications/[applicationId]/page.tsx"
  "src/app/(console)/console/audit/page.tsx"
  "src/app/(account)/profile/page.tsx"
  "src/app/(account)/profile/security/page.tsx"
  "src/app/(platform)/admin/page.tsx"
  "src/app/(oauth)/authorize/page.tsx"
  "src/app/(oauth)/discovery/page.tsx"
  src/lib/api/client.ts
  src/lib/api/problem.ts
  src/lib/api/endpoints/auth.ts
  src/lib/api/endpoints/organizations.ts
  src/lib/api/endpoints/users.ts
  src/lib/api/endpoints/rbac.ts
  src/lib/api/endpoints/applications.ts
  src/lib/api/endpoints/invitations.ts
  src/lib/api/endpoints/domains.ts
  src/lib/api/endpoints/settings.ts
  src/lib/api/endpoints/audit.ts
  src/lib/api/endpoints/mfa.ts
  src/lib/api/endpoints/sessions.ts
  src/lib/api/endpoints/platform.ts
  src/lib/api/endpoints/oidc.ts
  src/lib/auth/pkce.ts
  src/lib/auth/token-store.ts
  src/lib/format.ts
  src/stores/session-store.ts
  src/components/ui/button.tsx
  src/components/ui/input.tsx
  src/components/ui/card.tsx
  src/components/ui/badge.tsx
  src/components/ui/dialog.tsx
  src/components/ui/switch.tsx
  src/components/ui/select.tsx
  src/components/ui/textarea.tsx
  src/components/ui/empty-state.tsx
  src/components/ui/data-table.tsx
  src/components/ui/copy-button.tsx
  src/components/console/sidebar.tsx
  src/components/console/topbar.tsx
  src/components/console/user-menu.tsx
  src/components/console/org-switcher.tsx
  src/components/console/auth-guard.tsx
  src/components/console/stat-card.tsx
  src/components/console/page-header.tsx
  src/components/providers/app-providers.tsx
)

for f in "${required_files[@]}"; do
  if [[ -f "$f" ]]; then ok "$f"; else bad "missing: $f"; fi
done

# -----------------------------------------------------------------------------
section "2. Dependencies installed"
# -----------------------------------------------------------------------------
if [[ -d node_modules ]]; then
  ok "node_modules present"
else
  bad "node_modules missing — run: cd frontend && pnpm install"
fi

for pkg in next react @tanstack/react-query zustand axios react-hook-form zod tailwindcss lucide-react next-themes sonner class-variance-authority; do
  if [[ -d "node_modules/$pkg" ]] || [[ -d "node_modules/.pnpm" && $(ls node_modules/.pnpm 2>/dev/null | grep -c "^${pkg//\//+}@") -gt 0 ]]; then
    ok "$pkg"
  else
    bad "$pkg not installed"
  fi
done

# -----------------------------------------------------------------------------
section "3. TypeScript type-check"
# -----------------------------------------------------------------------------
if npx --no-install tsc --noEmit >/tmp/mip-tsc.log 2>&1; then
  ok "tsc --noEmit passes (0 errors)"
else
  bad "TypeScript errors — see /tmp/mip-tsc.log"
  tail -20 /tmp/mip-tsc.log | sed 's/^/    /'
fi

# -----------------------------------------------------------------------------
section "4. ESLint"
# -----------------------------------------------------------------------------
if pnpm --silent lint >/tmp/mip-lint.log 2>&1; then
  ok "pnpm lint passes"
else
  warn "lint warnings/errors — see /tmp/mip-lint.log"
  tail -15 /tmp/mip-lint.log | sed 's/^/    /'
fi

# -----------------------------------------------------------------------------
section "5. Production build"
# -----------------------------------------------------------------------------
if pnpm --silent build >/tmp/mip-build.log 2>&1; then
  ok "next build succeeded"
  route_count=$(grep -c '^├\|^└\|^┌' /tmp/mip-build.log || true)
  echo -e "     ${CYAN}Routes compiled:${RESET}"
  grep -E '^(├|└|┌) ' /tmp/mip-build.log | sed 's/^/       /'
else
  bad "next build failed — see /tmp/mip-build.log"
  tail -25 /tmp/mip-build.log | sed 's/^/    /'
fi

# -----------------------------------------------------------------------------
section "6. Environment configuration"
# -----------------------------------------------------------------------------
if [[ -f .env.local ]]; then
  ok ".env.local exists"
  grep -q '^NEXT_PUBLIC_API_BASE_URL=' .env.local && ok "NEXT_PUBLIC_API_BASE_URL set" || warn "NEXT_PUBLIC_API_BASE_URL missing"
else
  warn ".env.local missing — copy from .env.local.example"
fi

# -----------------------------------------------------------------------------
section "7. Backend connectivity (optional)"
# -----------------------------------------------------------------------------
API_URL="${NEXT_PUBLIC_API_BASE_URL:-http://localhost:8080}"
if [[ -f .env.local ]]; then
  API_URL=$(grep '^NEXT_PUBLIC_API_BASE_URL=' .env.local | cut -d= -f2- | tr -d '"')
  API_URL="${API_URL:-http://localhost:8080}"
fi

echo "   Probing $API_URL/actuator/health ..."
if curl -sf -o /dev/null -m 3 "$API_URL/actuator/health"; then
  ok "Backend reachable at $API_URL"
  echo "   Probing OpenAPI docs ..."
  if curl -sf -o /dev/null -m 3 "$API_URL/v3/api-docs"; then
    ok "OpenAPI spec exposed at /v3/api-docs"
  else
    warn "/v3/api-docs not reachable (not blocking)"
  fi
else
  warn "Backend not running at $API_URL"
  echo "     → Start with: docker-compose up -d postgres redis && mvn spring-boot:run"
fi

# -----------------------------------------------------------------------------
section "8. Dev server smoke test"
# -----------------------------------------------------------------------------
if pgrep -f "next dev" >/dev/null 2>&1; then
  running_port=$(ss -tlnp 2>/dev/null | grep -oP ':300[0-9]' | head -1 | tr -d ':')
  ok "next dev already running on port ${running_port:-?}"
  echo "     → Open http://localhost:${running_port:-3000}"
else
  echo "   Booting dev server for 8s ..."
  ( pnpm --silent dev >/tmp/mip-dev.log 2>&1 & echo $! >/tmp/mip-dev.pid )
  sleep 8
  DEV_PID=$(cat /tmp/mip-dev.pid 2>/dev/null || true)
  if grep -q "Ready in" /tmp/mip-dev.log 2>/dev/null; then
    port=$(grep -oP 'localhost:\K[0-9]+' /tmp/mip-dev.log | head -1)
    ok "Dev server ready on port ${port:-3000}"
    if curl -sf -o /dev/null -m 5 "http://localhost:${port:-3000}/login"; then
      ok "/login responds 200 OK"
    else
      bad "/login not reachable"
    fi
  else
    bad "Dev server failed to boot — see /tmp/mip-dev.log"
    tail -15 /tmp/mip-dev.log | sed 's/^/    /'
  fi
  [[ -n "${DEV_PID:-}" ]] && kill "$DEV_PID" 2>/dev/null || true
fi

# -----------------------------------------------------------------------------
echo -e "\n${BOLD}═══════════════════════════════════════════════════════════════${RESET}"
echo -e "${BOLD}Summary:${RESET} ${GREEN}$pass passed${RESET}  ${YELLOW}$warn warnings${RESET}  ${RED}$fail failed${RESET}"
echo -e "${BOLD}═══════════════════════════════════════════════════════════════${RESET}"

if (( fail > 0 )); then
  exit 1
fi

echo -e "\n${GREEN}${BOLD}✓ Frontend verification complete!${RESET}"
echo -e "\n${CYAN}Next steps:${RESET}"
echo -e "  1. ${BOLD}cd frontend && pnpm dev${RESET}      # start on http://localhost:3000"
echo -e "  2. Open ${BOLD}http://localhost:3000${RESET}  # redirects to /login"
echo -e "  3. Try /login /register /forgot-password /verify-email /mfa-challenge"
echo -e "  4. Start backend and log in to see /console shell"
