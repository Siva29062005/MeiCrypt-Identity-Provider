package com.meicrypt.identity.organization.entity;

/**
 * Enum defining the verification status of a custom domain.
 * Tracks the domain verification lifecycle.
 */
public enum DomainVerificationStatus {
    /**
     * Domain verification is pending - awaiting verification.
     */
    PENDING,
    
    /**
     * Domain has been successfully verified.
     */
    VERIFIED,
    
    /**
     * Domain verification failed or could not be completed.
     */
    FAILED
}
