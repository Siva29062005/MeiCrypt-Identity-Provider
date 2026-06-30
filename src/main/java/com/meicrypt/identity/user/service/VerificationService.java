package com.meicrypt.identity.user.service;

import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import com.meicrypt.identity.user.dto.ResendVerificationRequest;
import com.meicrypt.identity.user.dto.VerifyEmailRequest;
import com.meicrypt.identity.user.entity.User;
import com.meicrypt.identity.user.entity.UserStatus;
import com.meicrypt.identity.user.entity.VerificationToken;
import com.meicrypt.identity.user.entity.VerificationTokenStatus;
import com.meicrypt.identity.user.entity.VerificationTokenType;
import com.meicrypt.identity.user.exception.EmailAlreadyVerifiedException;
import com.meicrypt.identity.user.exception.InvalidTokenException;
import com.meicrypt.identity.user.repository.UserRepository;
import com.meicrypt.identity.user.repository.VerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for handling email and phone verification workflows.
 */
@Service
@Transactional
public class VerificationService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationService.class);
    private static final int VERIFICATION_TOKEN_EXPIRY_HOURS = 24;

    private final VerificationTokenRepository verificationTokenRepository;
    private final UserRepository userRepository;
    // TODO: Inject email service when implementing notification module

    public VerificationService(
            VerificationTokenRepository verificationTokenRepository,
            UserRepository userRepository) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.userRepository = userRepository;
    }

    /**
     * Send email verification token to user
     */
    public void sendEmailVerification(UUID userId) {
        logger.info("Sending email verification for user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        if (user.getEmailVerified()) {
            throw new EmailAlreadyVerifiedException(
                "Email is already verified for user: " + userId);
        }

        // Generate verification token
        String token = generateSecureToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(VERIFICATION_TOKEN_EXPIRY_HOURS);

        VerificationToken verificationToken = new VerificationToken(
            userId,
            token,
            VerificationTokenType.EMAIL_VERIFICATION,
            expiresAt
        );

        verificationTokenRepository.save(verificationToken);

        // TODO: Send email with verification link
        // Email should contain: {baseUrl}/verify-email?token={token}
        logger.info("Email verification token created for user: {}", userId);
        logger.debug("Verification token (for testing): {}", token);
    }

    /**
     * Verify email using verification token
     */
    public void verifyEmail(VerifyEmailRequest request) {
        logger.info("Verifying email with token");

        VerificationToken token = verificationTokenRepository.findByToken(request.token())
            .orElseThrow(() -> new InvalidTokenException("Invalid verification token"));

        // Check token type
        if (token.getTokenType() != VerificationTokenType.EMAIL_VERIFICATION) {
            throw new InvalidTokenException("Token is not an email verification token");
        }

        // Check if token is already used
        if (token.getStatus() == VerificationTokenStatus.USED) {
            throw new InvalidTokenException("Verification token has already been used");
        }

        // Check if token is expired
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setStatus(VerificationTokenStatus.EXPIRED);
            verificationTokenRepository.save(token);
            throw new InvalidTokenException("Verification token has expired");
        }

        // Get user and verify
        User user = userRepository.findById(token.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", token.getUserId().toString()));

        if (user.getEmailVerified()) {
            throw new EmailAlreadyVerifiedException("Email is already verified");
        }

        // Mark email as verified
        user.setEmailVerified(true);
        
        // Activate user if they were pending verification
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }

        userRepository.save(user);

        // Mark token as used
        token.setStatus(VerificationTokenStatus.USED);
        token.setUsedAt(LocalDateTime.now());
        verificationTokenRepository.save(token);

        logger.info("Email verified successfully for user: {}", user.getId());
    }

    /**
     * Resend email verification
     */
    public void resendEmailVerification(ResendVerificationRequest request) {
        logger.info("Resending email verification for: {}", request.email());

        User user = userRepository.findByEmailIgnoreCaseAndOrganizationId(
                request.email(), request.organizationId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "User", request.email() + " in organization " + request.organizationId()));

        if (user.getEmailVerified()) {
            throw new EmailAlreadyVerifiedException("Email is already verified");
        }

        // Invalidate existing pending tokens
        verificationTokenRepository.findValidTokensByUserAndType(
                user.getId(), VerificationTokenType.EMAIL_VERIFICATION)
            .forEach(token -> {
                token.setStatus(VerificationTokenStatus.REVOKED);
                verificationTokenRepository.save(token);
            });

        // Send new verification email
        sendEmailVerification(user.getId());
    }

    /**
     * Check if user has verified email
     */
    @Transactional(readOnly = true)
    public boolean isEmailVerified(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        return user.getEmailVerified();
    }

    /**
     * Clean up expired verification tokens
     * Should be called periodically by a scheduled job
     */
    public void cleanupExpiredTokens() {
        logger.info("Cleaning up expired verification tokens");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        verificationTokenRepository.deleteByStatusAndExpiresAtBefore(
            VerificationTokenStatus.EXPIRED, cutoffDate);
        
        logger.info("Expired verification tokens cleaned up");
    }

    /**
     * Generate a secure random token
     */
    private String generateSecureToken() {
        return UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
    }
}
