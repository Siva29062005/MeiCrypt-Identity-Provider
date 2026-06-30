package com.meicrypt.identity.user.entity;

/**
 * Enumeration of possible user account statuses.
 * Controls the lifecycle state of a user account.
 */
public enum UserStatus {
    /**
     * User account is active and can authenticate
     */
    ACTIVE,
    
    /**
     * User account is temporarily suspended (can be reactivated)
     */
    SUSPENDED,
    
    /**
     * User account is deactivated (soft delete, can be restored)
     */
    INACTIVE,
    
    /**
     * User account requires email verification before activation
     */
    PENDING_VERIFICATION
}
