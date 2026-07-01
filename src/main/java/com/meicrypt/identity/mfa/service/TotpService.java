package com.meicrypt.identity.mfa.service;

import com.meicrypt.identity.mfa.config.MfaProperties;
import com.meicrypt.identity.mfa.dto.TotpEnrollmentResponse;
import com.meicrypt.identity.mfa.entity.MfaFactorStatus;
import com.meicrypt.identity.mfa.entity.MfaFactorType;
import com.meicrypt.identity.mfa.entity.TotpEnrollment;
import com.meicrypt.identity.mfa.entity.UserMfaFactor;
import com.meicrypt.identity.mfa.exception.InvalidMfaCodeException;
import com.meicrypt.identity.mfa.exception.MfaFactorNotFoundException;
import com.meicrypt.identity.mfa.repository.TotpEnrollmentRepository;
import com.meicrypt.identity.mfa.repository.UserMfaFactorRepository;
import com.meicrypt.identity.user.entity.User;
import com.meicrypt.identity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Module 9.1 - onboarding + verification for authenticator apps.
 * <p>Flow:
 * <ol>
 *   <li>{@link #enrollTotp} generates a shared secret, persists a PENDING factor,
 *       and returns the otpauth URI + base64 PNG QR.</li>
 *   <li>{@link #verifyTotpEnrollment} accepts the first 6-digit code, marks the
 *       factor ACTIVE, and pins the counter to enforce anti-replay.</li>
 *   <li>{@link #verifyTotpCode} is called by
 *       {@code MfaChallengeService} during login step-up.</li>
 * </ol>
 */
@Service
@Transactional
public class TotpService {

    private static final Logger log = LoggerFactory.getLogger(TotpService.class);

    private final UserRepository userRepository;
    private final UserMfaFactorRepository factorRepository;
    private final TotpEnrollmentRepository totpRepository;
    private final TotpCodeGenerator codeGenerator;
    private final QrCodeService qrCodeService;
    private final MfaProperties mfaProperties;

    public TotpService(UserRepository userRepository,
                       UserMfaFactorRepository factorRepository,
                       TotpEnrollmentRepository totpRepository,
                       TotpCodeGenerator codeGenerator,
                       QrCodeService qrCodeService,
                       MfaProperties mfaProperties) {
        this.userRepository = userRepository;
        this.factorRepository = factorRepository;
        this.totpRepository = totpRepository;
        this.codeGenerator = codeGenerator;
        this.qrCodeService = qrCodeService;
        this.mfaProperties = mfaProperties;
    }

    public TotpEnrollmentResponse enrollTotp(UUID userId, String displayName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new MfaFactorNotFoundException("User not found: " + userId));

        UserMfaFactor factor = new UserMfaFactor(
                user.getId(), user.getOrganizationId(), MfaFactorType.TOTP, displayName);
        factor = factorRepository.save(factor);

        String secret = codeGenerator.newSecretBase32();
        String issuer = mfaProperties.issuer();
        String label = user.getEmail();
        TotpEnrollment enrollment = new TotpEnrollment(factor.getId(), secret, issuer, label);
        totpRepository.save(enrollment);

        String otpAuthUri = codeGenerator.otpAuthUri(
                issuer, label, secret,
                enrollment.getAlgorithm(), enrollment.getDigits(), enrollment.getPeriodSeconds());
        String qr = qrCodeService.generatePngBase64(otpAuthUri, 240);

        log.info("TOTP factor {} enrolled (PENDING) for user {}", factor.getId(), user.getId());
        return new TotpEnrollmentResponse(
                factor.getId(), secret, otpAuthUri, qr,
                enrollment.getDigits(), enrollment.getPeriodSeconds(), enrollment.getAlgorithm());
    }

    public void verifyTotpEnrollment(UUID userId, UUID factorId, String code) {
        UserMfaFactor factor = loadOwnedFactor(userId, factorId);
        if (factor.getStatus() != MfaFactorStatus.PENDING) {
            throw new InvalidMfaCodeException("Factor is not in PENDING state");
        }
        TotpEnrollment enrollment = totpRepository.findByFactorId(factorId)
                .orElseThrow(() -> new MfaFactorNotFoundException("TOTP enrollment not found"));

        TotpCodeGenerator.VerificationResult result = codeGenerator.verify(
                enrollment.getSecretBase32(), code, enrollment.getAlgorithm(),
                enrollment.getDigits(), enrollment.getPeriodSeconds(),
                mfaProperties.totpAllowedTimeStepSkew(), enrollment.getLastUsedCounter());

        if (!result.valid()) {
            throw new InvalidMfaCodeException("Invalid TOTP code");
        }

        enrollment.setLastUsedCounter(result.acceptedCounter());
        totpRepository.save(enrollment);

        factor.setStatus(MfaFactorStatus.ACTIVE);
        factor.setActivatedAt(Instant.now());
        factor.setLastUsedAt(Instant.now());
        factorRepository.save(factor);
        log.info("TOTP factor {} activated for user {}", factor.getId(), userId);
    }

    /**
     * Called from the login step-up path.
     * @return the factor that satisfied the check.
     */
    public UserMfaFactor verifyTotpCode(UUID userId, String code) {
        UserMfaFactor factor = factorRepository
                .findByUserIdAndFactorTypeAndStatus(userId, MfaFactorType.TOTP, MfaFactorStatus.ACTIVE)
                .orElseThrow(() -> new MfaFactorNotFoundException("No active TOTP factor for user"));
        TotpEnrollment enrollment = totpRepository.findByFactorId(factor.getId())
                .orElseThrow(() -> new MfaFactorNotFoundException("TOTP enrollment missing"));

        TotpCodeGenerator.VerificationResult result = codeGenerator.verify(
                enrollment.getSecretBase32(), code, enrollment.getAlgorithm(),
                enrollment.getDigits(), enrollment.getPeriodSeconds(),
                mfaProperties.totpAllowedTimeStepSkew(), enrollment.getLastUsedCounter());
        if (!result.valid()) {
            throw new InvalidMfaCodeException("Invalid TOTP code");
        }
        enrollment.setLastUsedCounter(result.acceptedCounter());
        totpRepository.save(enrollment);

        factor.setLastUsedAt(Instant.now());
        factorRepository.save(factor);
        return factor;
    }

    public void revokeFactor(UUID userId, UUID factorId) {
        UserMfaFactor factor = loadOwnedFactor(userId, factorId);
        factor.setStatus(MfaFactorStatus.REVOKED);
        factor.setRevokedAt(Instant.now());
        factorRepository.save(factor);
        log.info("TOTP factor {} revoked for user {}", factorId, userId);
    }

    private UserMfaFactor loadOwnedFactor(UUID userId, UUID factorId) {
        UserMfaFactor factor = factorRepository.findById(factorId)
                .orElseThrow(() -> new MfaFactorNotFoundException("Factor not found: " + factorId));
        if (!factor.getUserId().equals(userId)) {
            throw new MfaFactorNotFoundException("Factor not owned by user");
        }
        if (factor.getFactorType() != MfaFactorType.TOTP) {
            throw new MfaFactorNotFoundException("Factor is not a TOTP factor");
        }
        return factor;
    }
}
