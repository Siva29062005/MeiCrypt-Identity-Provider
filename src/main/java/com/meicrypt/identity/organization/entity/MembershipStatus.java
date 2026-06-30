package com.meicrypt.identity.organization.entity;

/**
 * Enum defining the status of an organization membership.
 * Controls whether a user can actively participate in the organization.
 */
public enum MembershipStatus {
    /**
     * Active membership with full access based on role.
     */
    ACTIVE,
    
    /**
     * Temporarily suspended membership.
     * User cannot access organization resources until reactivated.
     */
    SUSPENDED,
    
    /**
     * Inactive membership.
     * User has left or been removed from the organization.
     */
    INACTIVE
}
