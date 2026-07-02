# Frontend Verification Guide — MIP Console

Verify Phases **F0 (Bootstrap)** + **F1 (Auth UI)** + **F2 (Console Shell)**.

---

## ⚡ TL;DR — one command

```bash
chmod +x "Verification Scripts/verify-frontend.sh"
./"Verification Scripts/verify-frontend.sh"
```

The script auto-checks: file structure, deps, TypeScript, ESLint, production
build, env config, backend reachability, and dev-server smoke test. Exit code
`0` means everything green.

---

## 🧪 Step-by-step manual verification

### 1. Static checks (no server needed)

```bash
cd frontend

# Fresh install (only needed once)
pnpm install

# TypeScript — must be 0 errors
npx tsc --noEmit

# Lint — must be clean
pnpm lint

# Production build — must compile every route
pnpm build
```

Expected build output shows **18 static routes**:

```
Route (app)
┌ ○ /
├ ○ /login   /register   /forgot-password
├ ○ /verify-email   /mfa-challenge
├ ○ /console
├ ○ /console/users     /console/roles       /console/settings
├ ○ /console/invitations /console/domains   /console/applications
├ ○ /console/oauth-clients /console/audit
└ ○ /_not-found
```

---

### 2. Dev server smoke test (frontend only)

```bash
cd frontend
cp -n .env.local.example .env.local   # first time only
pnpm dev
```

Open **http://localhost:3000** — the root should redirect to `/login`.

Click through each auth page and confirm:

| Page | What to check |
|---|---|
| `/login` | Org + email + password inputs · password-eye toggle · “Forgot?” link · form validation errors under each field |
| `/register` | 12-char password rule enforced live |
| `/forgot-password` | Success alert after submit |
| `/verify-email` | Renders info alert when opened without `?token=` |
| `/mfa-challenge` | 6-digit input, redirects to `/login` if no challenge in store |

**With backend offline, form submits will show a red alert** *(Cannot reach the identity service.)* — this proves the RFC 7807 error interceptor works.

---

### 3. Full end-to-end with backend

Start the backend in another terminal:

```bash
# from repo root
docker-compose up -d postgres redis
mvn spring-boot:run
```

Then in the browser at `http://localhost:3000`:

1. **Register:** `/register` — org slug of an existing org, e.g. `acme`.
2. **Verify email:** open the token link the backend logs / sends.
3. **Login:** `/login` — after success you should land on **`/console`**.
4. **Console shell:** confirm
   - Sidebar with grouped nav (Overview · Directory · Organization · Developer · Security)
   - Sticky topbar with **Org Switcher** on the left showing your org slug
   - Global search input in the middle
   - **User menu (avatar)** on the right with theme toggle (Light/Dark/System) and Sign out
   - Dashboard cards render (skeleton loading), “Get started” checklist links work
5. **Nav stubs:** click Users / Roles / Settings / Invitations / Domains / Applications / OAuth Clients / Audit — each shows a polished “Landing in Phase Fx” card with a working ← back link.
6. **Sign out:** returns you to `/login` and clears the session.

---

### 4. Interceptor & auth guard verification

- **Auth guard:** open `http://localhost:3000/console/users` in an incognito
  window with no session → should redirect to
  `/login?returnTo=%2Fconsole%2Fusers`.
- **Silent refresh:** in DevTools > Application > Session Storage, delete the
  `mip.session` key. Navigate inside the console — API calls will 401,
  interceptor calls `/api/v1/auth/refresh` once, then either succeeds or
  bounces to `/login`.
- **Dark mode:** open user menu → pick “Dark” — the whole shell reskins
  instantly via `next-themes`.
- **Multi-tenant header:** open DevTools > Network on any authenticated
  request. Confirm `Authorization: Bearer …` and `X-Organization-Slug: <slug>`
  headers are present.

---

## ✅ Success criteria

- [x] `pnpm build` compiles with 0 errors and lists 18 routes
- [x] `npx tsc --noEmit` returns 0 errors
- [x] `pnpm lint` returns 0 errors
- [x] `/login` renders and shows validation errors on empty submit
- [x] Root `/` redirects to `/login`
- [x] `/console/*` routes redirect unauthenticated users to `/login?returnTo=…`
- [x] With a running backend, login → console shell → dashboard works
- [x] Sign-out clears the session and returns to `/login`
- [x] Theme toggle switches Light / Dark / System immediately

---

## 🩹 Troubleshooting

| Symptom | Fix |
|---|---|
| `ERR_PNPM_IGNORED_BUILDS: sharp / unrs-resolver` | Already handled via `frontend/pnpm-workspace.yaml`. If it re-appears, run `pnpm approve-builds sharp unrs-resolver`. |
| `useSearchParams should be wrapped in a suspense boundary` | Any client component using `useSearchParams` must be wrapped in `<Suspense>`. Login already is. |
| “Cannot reach the identity service” alert on `/login` | Backend is not on `NEXT_PUBLIC_API_BASE_URL`. Update `frontend/.env.local`. |
| Port 3000 in use | `pnpm dev` auto-falls back to 3001/3002 — check terminal output. |
| Dark mode flickers on load | Ensure `<html suppressHydrationWarning>` is present in `src/app/layout.tsx` (it is). |

---

## 🗺 Where things live

```
frontend/src/
├── env.ts                           # runtime env accessor
├── middleware.ts                    # request-id header
├── app/
│   ├── layout.tsx                   # <AppProviders> mount point
│   ├── page.tsx                     # / → /login redirect
│   ├── (auth)/                      # split-screen brand + form layout
│   └── (console)/                   # sidebar + topbar admin shell
├── components/
│   ├── ui/                          # Button, Input, Card, Dropdown, …
│   ├── console/                     # Sidebar, Topbar, UserMenu, StatCard
│   └── providers/                   # Query, Theme, Toaster
├── lib/
│   ├── api/                         # Axios client, RFC 7807 errors, endpoints
│   └── auth/                        # PKCE helpers, token store
└── stores/
    └── session-store.ts             # Zustand session state
```
