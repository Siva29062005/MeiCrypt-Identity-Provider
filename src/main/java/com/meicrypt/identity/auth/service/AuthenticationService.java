package com.meicrypt.identity.auth.service;

import com.meicrypt.identity.auth.config.JwtProperties;
import com.meicrypt.identity.auth.dto.LoginRequest;
import com.meicrypt.identity.auth.dto.LoginResponse;
import com.meicrypt.identity.auth.dto.TokenResponse;
import com.meicrypt.identity.mfa.dto.MfaChallengeDTO;
import com.meicrypt.identity.mfa.entity.MfaChallenge;
import com.meicrypt.identity.mfa.service.MfaChallengeService;
import com.meicrypt.identity.auth.entity.RefreshToken;
import com.meicrypt.identity.auth.entity.RefreshTokenStatus;
import com.meicrypt.identity.auth.entity.SessionStatus;
import com.meicrypt.identity.auth.entity.UserDevice;
import com.meicrypt.identity.auth.entity.UserSession;
import com.meicrypt.identity.auth.exception.AuthenticationFailedException;
import com.meicrypt.identity.auth.exception.InvalidRefreshTokenException;
import com.meicrypt.identity.auth.exception.RefreshTokenReuseException;
import com.meicrypt.identity.auth.repository.RefreshTokenRepository;
import com.meicrypt.identity.auth.repository.UserSessionRepository;
import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import com.meicrypt.identity.oauth.entity.OAuthRefreshTokenStatus;
import com.meicrypt.identity.oauth.repository.OAuthAccessTokenRepository;
import com.meicrypt.identity.oauth.repository.OAuthRefreshTokenRepository;
import com.meicrypt.identity.sso.service.BackchannelLogoutService;
import com.meicrypt.identity.sso.service.SsoSessionService;
import com.meicrypt.identity.user.entity.User;


import com.meicrypt.identity.user.entity.UserStatus;
import com.meicrypt.identity.user.exception.UserLockedException;
import com.meicrypt.identity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;


/**
 * Module 3.1 - 3.4:
 *   Credential verification, session bootstrap, refresh rotation with reuse detection,
 *   and Redis-based live session tracking.
 */
@Service
@Transactional
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 30;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionCacheService sessionCacheService;
    private final DeviceService deviceService;
    private final JwtProperties jwtProperties;
    private final SsoSessionService ssoSessionService;
    private final BackchannelLogoutService backchannelLogoutService;
    private final OAuthRefreshTokenRepository oauthRefreshTokenRepository;
    private final OAuthAccessTokenRepository oauthAccessTokenRepository;
    private final MfaChallengeService mfaChallengeService;

    public AuthenticationService(UserRepository userRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 UserSessionRepository sessionRepository,
                                 PasswordEncoder passwordEncoder,
                                 JwtService jwtService,
                                 SessionCacheService sessionCacheService,
                                 DeviceService deviceService,
                                 JwtProperties jwtProperties,
                                 SsoSessionService ssoSessionService,
                                 BackchannelLogoutService backchannelLogoutService,
                                 OAuthRefreshTokenRepository oauthRefreshTokenRepository,
                                 OAuthAccessTokenRepository oauthAccessTokenRepository,
                                 MfaChallengeService mfaChallengeService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionCacheService = sessionCacheService;
        this.deviceService = deviceService;
        this.jwtProperties = jwtProperties;
        this.ssoSessionService = ssoSessionService;
        this.backchannelLogoutService = backchannelLogoutService;
        this.oauthRefreshTokenRepository = oauthRefreshTokenRepository;
        this.oauthAccessTokenRepository = oauthAccessTokenRepository;
        this.mfaChallengeService = mfaChallengeService;
    }


    // ---------------- Module 3.1: Login (+ Phase 9 step-up) ----------------

    /**
     * Legacy entry point (Phase 3) - kept for backwards compatibility with
     * anything that has not yet migrated to {@link #loginWithMfa}. When the
     * user has an active MFA factor this method now refuses to issue tokens
     * and instead throws so callers cannot silently bypass step-up.
     */
    public TokenResponse login(LoginRequest request, String ipAddress, String userAgent) {
        LoginResponse response = loginWithMfa(request, ipAddress, userAgent);
        if (response.requiresMfa()) {
            throw new AuthenticationFailedException(
                    "MFA challenge required - use /api/v1/auth/login (v2) to receive the challenge token");
        }
        return response.tokens();
    }

    /**
     * Phase 9 entry point - performs the password check and, if the user has
     * any ACTIVE second factor, issues an {@link MfaChallengeDTO} instead of a
     * token pair. The client must redeem that challenge to complete the login.
     */
    public LoginResponse loginWithMfa(LoginRequest request, String ipAddress, String userAgent) {
        logger.info("Login attempt for email={} org={}", request.email(), request.organizationId());

        User user = userRepository
                .findByEmailIgnoreCaseAndOrganizationId(request.email(), request.organizationId())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid credentials"));

        assertAccountUsable(user);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailedAttempt(user);
            throw new AuthenticationFailedException("Invalid credentials");
        }

        Optional<MfaChallengeDTO> maybeChallenge = mfaChallengeService.maybeIssueChallengeForLogin(
                user.getId(), user.getOrganizationId(),
                ipAddress, userAgent,
                request.deviceFingerprint(), request.deviceName());
        if (maybeChallenge.isPresent()) {
            // Do NOT rotate lockout/login-timestamp columns yet - only real
            // token issuance marks the account as "logged in".
            logger.info("Password accepted for user {} - MFA challenge issued", user.getId());
            return LoginResponse.ofMfaChallenge(maybeChallenge.get());
        }

        // No MFA required → fall through to legacy Phase-3 token issuance.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);

        UserDevice device = deviceService.touchOrCreate(
                user.getId(), request.deviceFingerprint(), request.deviceName(), ipAddress, userAgent);
        UserSession session = openSession(user, device.getId(), ipAddress, userAgent);
        return LoginResponse.ofTokens(issueTokenPair(user, session, ipAddress, userAgent));
    }

    /**
     * Called by {@code MfaVerificationController} once a factor verifier has
     * signed off on the second-factor proof.  Completes the login by opening
     * a real session and issuing the token pair.
     */
    public TokenResponse completeLoginAfterMfa(MfaChallenge challenge,
                                               String ipAddress, String userAgent) {
        User user = userRepository.findById(challenge.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", challenge.getUserId().toString()));
        assertAccountUsable(user);

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);

        UserDevice device = deviceService.touchOrCreate(
                user.getId(), challenge.getDeviceFingerprint(), challenge.getDeviceName(),
                ipAddress, userAgent);
        UserSession session = openSession(user, device.getId(), ipAddress, userAgent);
        logger.info("MFA-completed login for user {} via factor {}",
                user.getId(), challenge.getSatisfiedFactorId());
        return issueTokenPair(user, session, ipAddress, userAgent);
    }

    private void assertAccountUsable(User user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new UserLockedException(
                    "Account locked due to too many failed login attempts", user.getLockedUntil());
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthenticationFailedException("Account is suspended");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new AuthenticationFailedException("Account is inactive");
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION || !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new AuthenticationFailedException("Email address has not been verified");
        }
    }

    private void recordFailedAttempt(User user) {
        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            logger.warn("User {} locked after {} failed attempts", user.getId(), attempts);
        }
        userRepository.save(user);
    }

    private UserSession openSession(User user, UUID deviceId, String ipAddress, String userAgent) {
        LocalDateTime expires = LocalDateTime.now().plusSeconds(jwtProperties.sessionTtlSeconds());
        UserSession session = new UserSession(
                user.getId(), user.getOrganizationId(), deviceId, ipAddress, userAgent, expires);
        session = sessionRepository.save(session);
        sessionCacheService.registerSession(session.getId());

        // Phase 8, Module 8.1: bootstrap the SSO shared session immediately
        // after the anchoring user_sessions row is written so subsequent OAuth
        // /authorize calls can silently seed multiple applications.
        try {
            Instant expiresAtInstant = expires.toInstant(ZoneOffset.UTC);
            ssoSessionService.openForLogin(session.getId(), user.getId(),
                    user.getOrganizationId(), ipAddress, userAgent, expiresAtInstant);
        } catch (Exception ex) {
            // SSO tracking is best-effort - never fail the login itself.
            logger.warn("Failed to open SSO session for user {}: {}", user.getId(), ex.getMessage());
        }
        return session;
    }


    private TokenResponse issueTokenPair(User user, UserSession session,
                                         String ipAddress, String userAgent) {
        String access = jwtService.issueAccessToken(
                user.getId(), user.getOrganizationId(), session.getId(), user.getEmail());
        String refresh = jwtService.generateOpaqueRefreshToken();
        String refreshHash = jwtService.hashOpaqueToken(refresh);
        LocalDateTime refreshExpiry = LocalDateTime.now()
                .plusSeconds(jwtProperties.refreshTokenTtlSeconds());
        refreshTokenRepository.save(new RefreshToken(
                user.getId(), user.getOrganizationId(), session.getId(),
                refreshHash, refreshExpiry, ipAddress, userAgent));
        return TokenResponse.of(
                access, refresh, jwtService.getAccessTokenTtlSeconds(),
                session.getId(), user.getId());
    }

    // ---------------- Module 3.2: Logout ----------------

    public void logout(String rawRefreshToken) {
        String hash = jwtService.hashOpaqueToken(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));
        terminateSession(token.getSessionId(), "USER_LOGOUT");
    }

    public void logoutSession(UUID sessionId) {
        terminateSession(sessionId, "SESSION_TERMINATED");
    }

    public void logoutAll(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.revokeAllByUser(userId, RefreshTokenStatus.REVOKED, now, "LOGOUT_ALL");
        for (UserSession s : sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE)) {
            s.setStatus(SessionStatus.TERMINATED);
            s.setTerminatedAt(now);
            s.setTerminationReason("LOGOUT_ALL");
            sessionRepository.save(s);
            sessionCacheService.terminateSession(s.getId());
        }
    }

    private void terminateSession(UUID sessionId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        Instant nowInstant = Instant.now();

        // Revoke the Phase-3 refresh chain first (owns the session).
        refreshTokenRepository.revokeAllBySession(sessionId, RefreshTokenStatus.REVOKED, now, reason);

        // Phase 8, Module 8.2: cascade Single Logout to every OAuth artifact
        // that shares this session so RP tokens die alongside the browser SSO.
        try {
            oauthRefreshTokenRepository.revokeAllForSession(
                    sessionId, OAuthRefreshTokenStatus.REVOKED, nowInstant, reason);
        } catch (Exception ex) {
            logger.warn("Failed to revoke OAuth refresh tokens for session {}: {}",
                    sessionId, ex.getMessage());
        }

        sessionRepository.findById(sessionId).ifPresent(s -> {
            if (s.getStatus() == SessionStatus.ACTIVE) {
                s.setStatus(SessionStatus.TERMINATED);
                s.setTerminatedAt(now);
                s.setTerminationReason(reason);
                sessionRepository.save(s);
            }
        });
        sessionCacheService.terminateSession(sessionId);

        // Fan out Back-Channel Logout notifications (Module 8.2).
        try {
            SsoSessionService.TerminationResult result =
                    ssoSessionService.terminate(sessionId, reason);
            if (result.hasParticipants()) {
                backchannelLogoutService.notifyParticipantsAsync(
                        result.session(), result.participants());
            }
        } catch (Exception ex) {
            logger.warn("SSO fan-out failed for session {}: {}", sessionId, ex.getMessage());
        }
    }


    // ---------------- Module 3.3: Rotating refresh ----------------

    public TokenResponse refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        String hash = jwtService.hashOpaqueToken(rawRefreshToken);
        RefreshToken presented = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));

        // Reuse detection: token already rotated / revoked → compromise the whole session
        if (presented.getStatus() != RefreshTokenStatus.ACTIVE) {
            LocalDateTime now = LocalDateTime.now();
            refreshTokenRepository.revokeAllBySession(
                    presented.getSessionId(), RefreshTokenStatus.COMPROMISED, now, "REUSE_DETECTED");
            terminateSession(presented.getSessionId(), "REFRESH_REUSE");
            throw new RefreshTokenReuseException(
                    "Refresh token reuse detected - session revoked");
        }
        if (presented.isExpired()) {
            presented.setStatus(RefreshTokenStatus.EXPIRED);
            refreshTokenRepository.save(presented);
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        UserSession session = sessionRepository.findById(presented.getSessionId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Session not found"));
        if (!session.isActive()) {
            throw new InvalidRefreshTokenException("Session is no longer active");
        }
        User user = userRepository.findById(presented.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", presented.getUserId().toString()));
        assertAccountUsable(user);

        // Rotate: mark old, issue new
        presented.setStatus(RefreshTokenStatus.ROTATED);
        presented.setRevokedAt(LocalDateTime.now());
        presented.setRevokedReason("ROTATED");
        refreshTokenRepository.save(presented);

        String newAccess = jwtService.issueAccessToken(
                user.getId(), user.getOrganizationId(), session.getId(), user.getEmail());
        String newRefresh = jwtService.generateOpaqueRefreshToken();
        String newRefreshHash = jwtService.hashOpaqueToken(newRefresh);
        RefreshToken child = new RefreshToken(
                user.getId(), user.getOrganizationId(), session.getId(),
                newRefreshHash,
                LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenTtlSeconds()),
                ipAddress, userAgent);
        child.setParentTokenHash(presented.getTokenHash());
        refreshTokenRepository.save(child);

        session.setLastActivityAt(LocalDateTime.now());
        sessionRepository.save(session);
        sessionCacheService.touchSession(session.getId());

        return TokenResponse.of(newAccess, newRefresh,
                jwtService.getAccessTokenTtlSeconds(), session.getId(), user.getId());
    }
}
