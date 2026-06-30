package com.meicrypt.identity.organization.entity;

/**
 * Enumeration of possible organization statuses.
 * Controls the operational state of an organization in the system.
 */
public enum OrganizationStatus {
    
    /**
     * Organization is active and fully operational.
     * Users can authenticate and access resources.
     */
    ACTIVE,
    
    /**
     * Organization is temporarily suspended.
     * Access is blocked but data is preserved.
     */
    SUSPENDED,
    
    /**
     * Organization is inactive.
     * Typically used for soft-deleted or archived organizations.
     */
    INACTIVE
}
