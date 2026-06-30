package com.meicrypt.identity.user.repository;

import com.meicrypt.identity.user.entity.User;
import com.meicrypt.identity.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entity operations.
 * Handles database access for user management.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email address (case-insensitive)
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Find user by email and organization
     */
    Optional<User> findByEmailIgnoreCaseAndOrganizationId(String email, UUID organizationId);

    /**
     * Check if email exists in organization
     */
    boolean existsByEmailIgnoreCaseAndOrganizationId(String email, UUID organizationId);

    /**
     * Find all users in an organization
     */
    List<User> findByOrganizationId(UUID organizationId);

    /**
     * Find users by organization and status
     */
    List<User> findByOrganizationIdAndStatus(UUID organizationId, UserStatus status);

    /**
     * Find active users in organization
     */
    @Query("SELECT u FROM User u WHERE u.organizationId = :orgId AND u.status = 'ACTIVE'")
    List<User> findActiveUsersByOrganization(@Param("orgId") UUID organizationId);

    /**
     * Count users in organization
     */
    long countByOrganizationId(UUID organizationId);

    /**
     * Count active users in organization
     */
    long countByOrganizationIdAndStatus(UUID organizationId, UserStatus status);

    /**
     * Find users with failed login attempts exceeding threshold
     */
    @Query("SELECT u FROM User u WHERE u.failedLoginAttempts >= :threshold AND u.status = 'ACTIVE'")
    List<User> findUsersWithFailedLoginAttempts(@Param("threshold") int threshold);

    /**
     * Find locked users
     */
    @Query("SELECT u FROM User u WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil > CURRENT_TIMESTAMP")
    List<User> findLockedUsers();

    /**
     * Find users who haven't logged in since a specific date
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt < :since OR u.lastLoginAt IS NULL")
    List<User> findInactiveUsersSince(@Param("since") LocalDateTime since);

    /**
     * Find users pending verification
     */
    List<User> findByStatusAndOrganizationId(UserStatus status, UUID organizationId);

    /**
     * Find users by phone number
     */
    Optional<User> findByPhoneNumberAndOrganizationId(String phoneNumber, UUID organizationId);

    /**
     * Check if phone number exists in organization
     */
    boolean existsByPhoneNumberAndOrganizationId(String phoneNumber, UUID organizationId);
}
