package com.meicrypt.identity.user.entity;

/**
 * Enumeration of verification token statuses.
 */
public enum VerificationTokenStatus {
    /**
     * Token is pending use
     */
    PENDING,
    
    /**
     * Token has been successfully used
     */
    USED,
    
    /**
     * Token has expired
     */
    EXPIRED,
    
    /**
     * Token has been revoked/invalidated
     */
    REVOKED
}
