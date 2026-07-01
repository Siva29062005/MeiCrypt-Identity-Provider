# Phase 9 Verification - Advanced Multi-Factor Authentication (MFA)

**Blueprint reference:** *MeiCrypt Identity Platform (MIP) - Phase 8 & 9 - SSO Federation & Advanced Multi-Factor Authentication (MFA)*

Phase 9 adds a second-factor step to the login flow:

* **Module 9.1 - TOTP Authenticator Apps** (RFC 6238)
  → onboarding flows generating standard scanning QR codes for apps like
  Google Authenticator / Authy / 1Password.
* **Module 9.2 - Passkey / WebAuthn Integrations** (WebAuthn Level 2)
  → hardware-backed, passwordless authentication using webauthn4j for full
  attestation / assertion validation.

The password check itself did not change; when a user has at least one
**ACTIVE** MFA factor the login endpoint now returns a short-lived
`challenge_token` and `HTTP 202 Accepted` instead of a token pair. That
challenge is redeemed at `POST /api/v1/mfa/challenges/verify` with a
second-factor proof.

---

## 1. New Files (Phase 9)

### Migrations
```
src/main/resources/db/migration/
└── V11__mfa_factors_and_challenges.sql
```
Creates:
* `user_mfa_factors`      – abstract factor per user (TOTP / WEBAUTHN)
* `totp_enrollments`      – RFC 6238 secret + config
* `webauthn_credentials`  – Passkey public keys + counters
* `webauthn_challenges`   – one-shot registration / assertion challenges
* `mfa_challenges`        – login-time step-up state

### Java module (`com.meicrypt.identity.mfa`)
```
mfa/
├── config/
│   └── MfaProperties.java           # bound to meicrypt.mfa.*
├── controller/
│   ├── MfaFactorController.java     # GET /api/v1/mfa/factors
│   ├── TotpController.java          # /api/v1/mfa/totp/**
│   ├── WebAuthnController.java      # /api/v1/mfa/webauthn/**
│   └── MfaChallengeController.java  # POST /api/v1/mfa/challenges/verify
├── dto/
│   ├── MfaFactorDTO, MfaChallengeDTO
│   ├── EnrollTotpRequest, VerifyTotpEnrollmentRequest, TotpEnrollmentResponse
│   ├── EnrollWebAuthnRequest, CompleteWebAuthnRegistrationRequest
│   ├── WebAuthnRegistrationOptions, WebAuthnAssertionOptions
│   ├── WebAuthnAssertionPayload
│   └── VerifyMfaChallengeRequest
├── entity/
│   ├── MfaFactorType, MfaFactorStatus
│   ├── UserMfaFactor
│   ├── TotpEnrollment
│   ├── WebAuthnCredential
│   ├── WebAuthnChallenge
│   └── MfaChallenge
├── exception/
│   ├── MfaException (base)
│   ├── MfaFactorNotFoundException
│   ├── InvalidMfaCodeException
│   ├── MfaChallengeNotFoundException
│   ├── InvalidMfaChallengeStateException
│   └── WebAuthnVerificationException
├── mapper/
│   └── MfaFactorMapper
├── repository/
│   ├── UserMfaFactorRepository
│   ├── TotpEnrollmentRepository
│   ├── WebAuthnCredentialRepository
│   ├── WebAuthnChallengeRepository
│   └── MfaChallengeRepository
└── service/
    ├── TotpCodeGenerator           # pure RFC 6238 HMAC generator/verifier
    ├── QrCodeService               # ZXing PNG QR encoder
    ├── TotpService                 # Module 9.1 orchestrator
    ├── WebAuthnService             # Module 9.2 orchestrator
    └── MfaChallengeService         # cross-factor login step-up
```

### Modified files
| File | Purpose |
|------|---------|
| `pom.xml` | Adds `com.webauthn4j:webauthn4j-core` and `com.google.zxing:core/javase` |
| `src/main/resources/application.yml` | New `meicrypt.mfa.*` block |
| `MeicryptIdentityApplication.java` | `@EnableConfigurationProperties(MfaProperties.class)` |
| `SecurityConfiguration.java` | `permitAll` on `/api/v1/mfa/challenges/verify` |
| `GlobalExceptionHandler.java` | RFC 7807 handlers for all 5 MFA exceptions |
| `auth/service/AuthenticationService.java` | `loginWithMfa` + `completeLoginAfterMfa` orchestrating step-up |
| `auth/controller/AuthenticationController.java` | Returns `LoginResponse` (tokens **or** challenge) |
| `auth/dto/LoginResponse.java` (new) | Discriminated login response |

---

## 2. REST Surface

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET`    | `/api/v1/mfa/factors`                                | JWT     | List all registered factors for the caller |
| `POST`   | `/api/v1/mfa/totp/enroll`                            | JWT     | Begin TOTP enrolment → secret, QR, otpauth URI |
| `POST`   | `/api/v1/mfa/totp/factors/{factorId}/verify`         | JWT     | Activate a PENDING TOTP factor with the first code |
| `DELETE` | `/api/v1/mfa/totp/factors/{factorId}`                | JWT     | Revoke a TOTP factor |
| `POST`   | `/api/v1/mfa/webauthn/register/begin`                | JWT     | Return `PublicKeyCredentialCreationOptions` |
| `POST`   | `/api/v1/mfa/webauthn/register/complete`             | JWT     | Verify attestation → activate Passkey factor |
| `POST`   | `/api/v1/mfa/webauthn/assertion/options`             | JWT     | Return `PublicKeyCredentialRequestOptions` |
| `DELETE` | `/api/v1/mfa/webauthn/factors/{factorId}`            | JWT     | Revoke a Passkey factor |
| `POST`   | `/api/v1/mfa/challenges/verify`                      | **anon (challenge_token)** | Redeem an MFA challenge for a real token pair |
| `POST`   | `/api/v1/auth/login`                                 | anon    | Password login – returns `TokenResponse` **or** `MfaChallengeDTO` |

---

## 3. Login Step-Up Flow

```
┌───────────┐    POST /api/v1/auth/login (email+password)
│  Client   │──────────────────────────────────────────►
│           │◄──────── 202 Accepted { mfaChallenge:{token,…} }  (if MFA enabled)
│           │
│           │    POST /api/v1/mfa/challenges/verify
│           │       { challengeToken, factorType, proof }
│           │──────────────────────────────────────────►
│           │◄──────── 200 OK { access_token, refresh_token, … }
└───────────┘
```

Behind the scenes on `verify`:

1. `MfaChallengeService.loadForVerification()` – challenge must be `PENDING`,
   unexpired, and known to the DB.
2. Depending on `factorType`:
   * **TOTP** – `TotpService.verifyTotpCode(user, code)` performs a
     ±1 window HMAC check and pins `last_used_counter` (anti-replay).
   * **WEBAUTHN** – `WebAuthnService.verifyAssertion(user, payload)` runs
     the full webauthn4j Level-2 assertion validation (RP-ID hash,
     client-data hash, signature, sign-counter, backup/UV flags).
3. On success the challenge is `SATISFIED`, `AuthenticationService.completeLoginAfterMfa`
   opens a session (touching `UserDevice`, seeding SSO), and issues the
   Phase-3 access/refresh pair.
4. Every failure increments `attempts`; after `meicrypt.mfa.max-challenge-attempts`
   the row transitions to `FAILED`.

---

## 4. Configuration Keys

Under `meicrypt.mfa` in `application.yml`:

```yaml
meicrypt:
  mfa:
    issuer: MeiCrypt                     # otpauth issuer
    relying-party-id: localhost          # WebAuthn RP-ID (must match origin host)
    relying-party-name: MeiCrypt Identity
    relying-party-origin: http://localhost:8080
    challenge-ttl-seconds: 300           # 5-min step-up window
    webauthn-challenge-ttl-seconds: 300
    totp-allowed-time-step-skew: 1       # ±30 seconds
    max-challenge-attempts: 5
```

All parameters are also driven by environment variables
(`MEICRYPT_MFA_ISSUER`, `MEICRYPT_MFA_RP_ID`, …) so production overrides
never require config-file changes.

---

## 5. Compile Verification

```
$ mvn -q -DskipTests compile
BUILD SUCCESS
```

Run the DB migration + wire-up smoke test with `./verify-phase9.sh`.
