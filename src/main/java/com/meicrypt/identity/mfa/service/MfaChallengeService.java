package com.meicrypt.identity.mfa.service;

import com.meicrypt.identity.mfa.config.MfaProperties;
import com.meicrypt.identity.mfa.dto.MfaChallengeDTO;
import com.meicrypt.identity.mfa.entity.MfaChallenge;
import com.meicrypt.identity.mfa.entity.MfaFactorStatus;
import com.meicrypt.identity.mfa.entity.MfaFactorType;
import com.meicrypt.identity.mfa.entity.UserMfaFactor;
import com.meicrypt.identity.mfa.exception.InvalidMfaChallengeStateException;
import com.meicrypt.identity.mfa.exception.MfaChallengeNotFoundException;
import com.meicrypt.identity.mfa.repository.MfaChallengeRepository;
import com.meicrypt.identity.mfa.repository.UserMfaFactorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Central Phase 9 orchestrator - decides whether a login attempt requires
 * step-up, issues opaque challenge tokens, and gates factor verification.
 *
 * <p>The {@code AuthenticationService} calls {@link #maybeIssueChallengeForLogin}
 * after the password check succeeds. If a challenge is issued, the caller must
 * surface it to the client instead of returning tokens.
 */
@Service
@Transactional
public class MfaChallengeService {

    private static final Logger log = LoggerFactory.getLogger(MfaChallengeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private final UserMfaFactorRepository factorRepository;
    private final MfaChallengeRepository challengeRepository;
    private final MfaProperties mfaProperties;

    public MfaChallengeService(UserMfaFactorRepository factorRepository,
                               MfaChallengeRepository challengeRepository,
                               MfaProperties mfaProperties) {
        this.factorRepository = factorRepository;
        this.challengeRepository = challengeRepository;
        this.mfaProperties = mfaProperties;
    }

    /**
     * @return {@code true} if the user has at least one ACTIVE second factor.
     */
    public boolean userRequiresMfa(UUID userId) {
        return factorRepository.existsByUserIdAndStatus(userId, MfaFactorStatus.ACTIVE);
    }

    public Optional<MfaChallengeDTO> maybeIssueChallengeForLogin(UUID userId,
                                                                UUID organizationId,
                                                                String ipAddress,
                                                                String userAgent,
                                                                String deviceFingerprint,
                                                                String deviceName) {
        List<UserMfaFactor> factors =
                factorRepository.findByUserIdAndStatus(userId, MfaFactorStatus.ACTIVE);
        if (factors.isEmpty()) return Optional.empty();

        String allowedTypes = factors.stream()
                .map(UserMfaFactor::getFactorType)
                .distinct()
                .map(Enum::name)
                .collect(Collectors.joining(","));

        String token = newChallengeToken();
        Instant expiresAt = Instant.now().plusSeconds(mfaProperties.challengeTtlSeconds());
        MfaChallenge challenge = challengeRepository.save(new MfaChallenge(
                userId, organizationId, token, allowedTypes, expiresAt,
                ipAddress, userAgent, deviceFingerprint, deviceName));

        log.info("MFA challenge {} issued for user {} (factors: {})",
                challenge.getId(), userId, allowedTypes);
        return Optional.of(new MfaChallengeDTO(
                challenge.getId(), token, Arrays.asList(allowedTypes.split(",")), expiresAt));
    }

    /**
     * Marks the challenge as satisfied after any factor verifier returns success.
     * Called from the login-step-up controller once TOTP or WebAuthn signs off.
     * @return the challenge row (with user + org needed to complete the login).
     */
    public MfaChallenge markSatisfied(String challengeToken, MfaFactorType satisfiedBy, UUID factorId) {
        MfaChallenge challenge = loadForVerification(challengeToken);
        if (!Arrays.asList(challenge.getAllowedFactorTypes().split(",")).contains(satisfiedBy.name())) {
            throw new InvalidMfaChallengeStateException(
                    "Factor type " + satisfiedBy + " not accepted for this challenge");
        }
        challenge.setStatus(MfaChallenge.Status.SATISFIED);
        challenge.setSatisfiedAt(Instant.now());
        challenge.setSatisfiedFactorId(factorId);
        return challengeRepository.save(challenge);
    }

    public void recordFailedAttempt(String challengeToken) {
        MfaChallenge challenge = challengeRepository.findByChallengeToken(challengeToken)
                .orElseThrow(() -> new MfaChallengeNotFoundException("Challenge not found"));
        challenge.incrementAttempts();
        if (challenge.getAttempts() >= mfaProperties.maxChallengeAttempts()) {
            challenge.setStatus(MfaChallenge.Status.FAILED);
            log.warn("MFA challenge {} exhausted attempts and was marked FAILED", challenge.getId());
        }
        challengeRepository.save(challenge);
    }

    public MfaChallenge loadForVerification(String challengeToken) {
        MfaChallenge challenge = challengeRepository.findByChallengeToken(challengeToken)
                .orElseThrow(() -> new MfaChallengeNotFoundException("Challenge not found"));
        if (challenge.getStatus() != MfaChallenge.Status.PENDING) {
            throw new InvalidMfaChallengeStateException(
                    "Challenge is not in PENDING state (was " + challenge.getStatus() + ")");
        }
        if (challenge.isExpired()) {
            challenge.setStatus(MfaChallenge.Status.EXPIRED);
            challengeRepository.save(challenge);
            throw new InvalidMfaChallengeStateException("Challenge has expired");
        }
        return challenge;
    }

    private String newChallengeToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        // 48 raw bytes → 64 base64url chars, well under our 128-char column.
        return B64URL.encodeToString(bytes);
    }

    // Kept for future audit trails; unused otherwise.
    @SuppressWarnings("unused")
    private String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
