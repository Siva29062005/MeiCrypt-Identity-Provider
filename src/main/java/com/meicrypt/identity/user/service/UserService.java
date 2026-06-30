package com.meicrypt.identity.user.service;

import com.meicrypt.identity.common.exception.DuplicateResourceException;
import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import com.meicrypt.identity.organization.entity.OrganizationSettings;
import com.meicrypt.identity.organization.repository.OrganizationRepository;
import com.meicrypt.identity.organization.repository.OrganizationSettingsRepository;
import com.meicrypt.identity.user.dto.*;
import com.meicrypt.identity.user.entity.User;
import com.meicrypt.identity.user.entity.UserStatus;
import com.meicrypt.identity.user.exception.EmailAlreadyVerifiedException;
import com.meicrypt.identity.user.exception.InvalidPasswordException;
import com.meicrypt.identity.user.mapper.UserMapper;
import com.meicrypt.identity.user.repository.UserRepository;
import com.meicrypt.identity.user.validation.PasswordValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for user management operations.
 * Handles user registration, profile management, and lifecycle operations.
 */
@Service
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationSettingsRepository organizationSettingsRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final VerificationService verificationService;

    public UserService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            OrganizationSettingsRepository organizationSettingsRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            PasswordValidator passwordValidator,
            VerificationService verificationService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
        this.verificationService = verificationService;
    }

    /**
     * Register a new user
     */
    public UserDTO registerUser(RegisterUserRequest request) {
        logger.info("Registering new user with email: {} for organization: {}", 
            request.email(), request.organizationId());

        // Verify organization exists
        organizationRepository.findById(request.organizationId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Organization", request.organizationId().toString()));

        // Check if email already exists in organization
        if (userRepository.existsByEmailIgnoreCaseAndOrganizationId(
                request.email(), request.organizationId())) {
            throw new DuplicateResourceException(
                "User with email " + request.email() + " already exists in this organization", 
                "email");
        }

        // Get organization settings for password validation
        OrganizationSettings settings = organizationSettingsRepository
            .findByOrganizationId(request.organizationId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "OrganizationSettings", request.organizationId().toString()));

        // Validate password
        PasswordValidator.ValidationResult validationResult = 
            passwordValidator.validate(request.password(), settings);
        if (!validationResult.isValid()) {
            throw new InvalidPasswordException(validationResult.getErrorMessage());
        }

        // Check for common weak passwords
        if (passwordValidator.isCommonPassword(request.password())) {
            throw new InvalidPasswordException(
                "Password is too common. Please choose a stronger password.");
        }

        // Create user entity
        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPasswordChangedAt(LocalDateTime.now());

        // Set default values if not provided
        if (user.getLocale() == null) {
            user.setLocale(settings.getPrimaryLanguage());
        }
        if (user.getTimezone() == null) {
            user.setTimezone(settings.getPrimaryTimezone());
        }

        // Set display name if not provided
        if (user.getDisplayName() == null && user.getFirstName() != null) {
            user.setDisplayName(user.getFirstName() + 
                (user.getLastName() != null ? " " + user.getLastName() : ""));
        }

        User savedUser = userRepository.save(user);
        logger.info("User registered successfully with ID: {}", savedUser.getId());

        // Send verification email asynchronously
        verificationService.sendEmailVerification(savedUser.getId());

        return userMapper.toDTO(savedUser);
    }

    /**
     * Get user by ID
     */
    @Transactional(readOnly = true)
    public UserDTO getUserById(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        return userMapper.toDTO(user);
    }

    /**
     * Get user by email and organization
     */
    @Transactional(readOnly = true)
    public UserDTO getUserByEmailAndOrganization(String email, UUID organizationId) {
        User user = userRepository.findByEmailIgnoreCaseAndOrganizationId(email, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "User", email + " in organization " + organizationId));
        return userMapper.toDTO(user);
    }

    /**
     * Get all users in an organization
     */
    @Transactional(readOnly = true)
    public List<UserDTO> getUsersByOrganization(UUID organizationId) {
        List<User> users = userRepository.findByOrganizationId(organizationId);
        return users.stream()
            .map(userMapper::toDTO)
            .toList();
    }

    /**
     * Get active users in an organization
     */
    @Transactional(readOnly = true)
    public List<UserDTO> getActiveUsersByOrganization(UUID organizationId) {
        List<User> users = userRepository.findActiveUsersByOrganization(organizationId);
        return users.stream()
            .map(userMapper::toDTO)
            .toList();
    }

    /**
     * Update user profile
     */
    public UserDTO updateUserProfile(UUID userId, UpdateUserProfileRequest request) {
        logger.info("Updating profile for user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        // Update only non-null fields
        userMapper.updateUserFromDTO(request, user);

        // Update display name if first/last name changed
        if (request.firstName() != null || request.lastName() != null) {
            String displayName = (user.getFirstName() != null ? user.getFirstName() : "") +
                (user.getLastName() != null ? " " + user.getLastName() : "");
            user.setDisplayName(displayName.trim());
        }

        User updatedUser = userRepository.save(user);
        logger.info("Profile updated successfully for user: {}", userId);

        return userMapper.toDTO(updatedUser);
    }

    /**
     * Change user password
     */
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        logger.info("Changing password for user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        // Verify current password
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

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
        logger.info("Password changed successfully for user: {}", userId);
    }

    /**
     * Suspend user account
     */
    public UserDTO suspendUser(UUID userId) {
        logger.info("Suspending user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        user.setStatus(UserStatus.SUSPENDED);
        User savedUser = userRepository.save(user);

        logger.info("User suspended successfully: {}", userId);
        return userMapper.toDTO(savedUser);
    }

    /**
     * Activate user account
     */
    public UserDTO activateUser(UUID userId) {
        logger.info("Activating user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        // Only activate if email is verified
        if (!user.getEmailVerified()) {
            throw new IllegalStateException("Cannot activate user with unverified email");
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        User savedUser = userRepository.save(user);

        logger.info("User activated successfully: {}", userId);
        return userMapper.toDTO(savedUser);
    }

    /**
     * Deactivate user account (soft delete)
     */
    public UserDTO deactivateUser(UUID userId) {
        logger.info("Deactivating user: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        user.setStatus(UserStatus.INACTIVE);
        User savedUser = userRepository.save(user);

        logger.info("User deactivated successfully: {}", userId);
        return userMapper.toDTO(savedUser);
    }

    /**
     * Delete user permanently (hard delete)
     */
    public void deleteUser(UUID userId) {
        logger.info("Deleting user permanently: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        userRepository.delete(user);
        logger.info("User deleted permanently: {}", userId);
    }

    /**
     * Count users in organization
     */
    @Transactional(readOnly = true)
    public long countUsersByOrganization(UUID organizationId) {
        return userRepository.countByOrganizationId(organizationId);
    }

    /**
     * Count active users in organization
     */
    @Transactional(readOnly = true)
    public long countActiveUsersByOrganization(UUID organizationId) {
        return userRepository.countByOrganizationIdAndStatus(organizationId, UserStatus.ACTIVE);
    }

    /**
     * Verify if email already exists in organization
     */
    @Transactional(readOnly = true)
    public boolean emailExistsInOrganization(String email, UUID organizationId) {
        return userRepository.existsByEmailIgnoreCaseAndOrganizationId(email, organizationId);
    }

    /**
     * Record failed login attempt
     */
    public void recordFailedLoginAttempt(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

        // Lock account after 5 failed attempts
        if (user.getFailedLoginAttempts() >= 5) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
            logger.warn("User account locked due to failed login attempts: {}", userId);
        }

        userRepository.save(user);
    }

    /**
     * Record successful login
     */
    public void recordSuccessfulLogin(UUID userId, String ipAddress) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        userRepository.save(user);
    }
}
