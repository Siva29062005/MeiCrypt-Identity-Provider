package com.meicrypt.identity.user.service;

import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import com.meicrypt.identity.organization.entity.OrganizationSettings;
import com.meicrypt.identity.organization.repository.OrganizationSettingsRepository;
import com.meicrypt.identity.user.dto.InitiatePasswordResetRequest;
import com.meicrypt.identity.user.dto.ResetPasswordRequest;
import com.meicrypt.identity.user.entity.PasswordResetToken;
import com.meicrypt.identity.user.entity.User;
import com.meicrypt.identity.user.entity.VerificationTokenStatus;
import com.meicrypt.identity.user.exception.InvalidPasswordException;
import com.meicrypt.identity.user.exception.InvalidTokenException;
import com.meicrypt.identity.user.repository.PasswordResetTokenRepository;
import com.meicrypt.identity.user.repository.UserRepository;
import com.meicrypt.identity.user.validation.PasswordValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for handling password reset workflows.
 */
@Service
@Transactional
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int PASSWORD_RESET_TOKEN_EXPIRY_HOURS = 1; // Short expiry for security

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final OrganizationSettingsRepository organizationSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    // TODO: Inject email service when implementing notification module

    public PasswordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserRepository userRepository,
            OrganizationSettingsRepository organizationSettingsRepository,
            PasswordEncoder passwordEncoder,
            PasswordValidator passwordValidator) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userRepository = userRepository;
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
    }

    /**
     * Initiate password reset process
     * Sends reset link to user's email
     */
    public void initiatePasswordReset(InitiatePasswordResetRequest request) {
        logger.info("Initiating password reset for email: {}", request.email());

        // Find user (fail silently for security - don't reveal if email exists)
        User user = userRepository.findByEmailIgnoreCaseAndOrganizationId(
                request.email(), request.organizationId())
            .orElse(null);

        if (user == null) {
            // Log but don't throw exception (security: don't reveal user existence)
            logger.warn("Password reset requested for non-existent email: {}", request.email());
            return;
        }

        // Invalidate any existing pending tokens for this user
        passwordResetTokenRepository.invalidateAllPendingTokensForUser(user.getId());

        // Generate reset token
        String token = generateSecureToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(PASSWORD_RESET_TOKEN_EXPIRY_HOURS);

        PasswordResetToken resetToken = new PasswordResetToken(
            user.getId(),
            token,
            expiresAt
        );

        passwordResetTokenRepository.save(resetToken);

        // TODO: Send email with reset link
        // Email should contain: {baseUrl}/reset-password?token={token}
        logger.info("Password reset token created for user: {}", user.getId());
        logger.debug("Password reset token (for testing): {}", token);
    }

    /**
     * Reset password using reset token
     */
    public void resetPassword(ResetPasswordRequest request) {
        logger.info("Resetting password with token");

        // Find and validate token
        PasswordResetToken token = passwordResetTokenRepository.findValidToken(request.token())
            .orElseThrow(() -> new InvalidTokenException(
                "Invalid or expired password reset token"));

        // Get user
        User user = userRepository.findById(token.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", token.getUserId().toString()));

        // Get organization settings for password validation
        OrganizationSettings settings = organizationSettingsRepository
            .findByOrganizationId(user.getOrganizationId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "OrganizationSettings", user.getOrganizationId().toString()));

        // Validate new password
        PasswordValidator.ValidationResult validationResult = 
            passwordValidator.validate(request.newPassword(), settings);
        if (!validationResult.isValid()) {
            throw new InvalidPasswordException(validationResult.getErrorMessage());
        }

        // Check for common weak passwords
        if (passwordValidator.isCommonPassword(request.newPassword())) {
            throw new InvalidPasswordException(
                "Password is too common. Please choose a stronger password.");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0); // Reset failed attempts
        user.setLockedUntil(null); // Unlock if locked

        userRepository.save(user);

        // Mark token as used
        token.setStatus(VerificationTokenStatus.USED);
        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        logger.info("Password reset successfully for user: {}", user.getId());
    }

    /**
     * Validate if a password reset token is valid
     */
    @Transactional(readOnly = true)
    public boolean isTokenValid(String token) {
        return passwordResetTokenRepository.findValidToken(token).isPresent();
    }

    /**
     * Clean up expired password reset tokens
     * Should be called periodically by a scheduled job
     */
    public void cleanupExpiredTokens() {
        logger.info("Cleaning up expired password reset tokens");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(1);
        passwordResetTokenRepository.deleteByStatusAndExpiresAtBefore(
            VerificationTokenStatus.EXPIRED, cutoffDate);
        
        logger.info("Expired password reset tokens cleaned up");
    }

    /**
     * Generate a secure random token
     */
    private String generateSecureToken() {
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
    }
}
