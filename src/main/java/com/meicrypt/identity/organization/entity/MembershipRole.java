package com.meicrypt.identity.organization.entity;

/**
 * Enum defining roles available for organization memberships.
 * Determines access levels and permissions within an organization.
 */
public enum MembershipRole {
    /**
     * Organization owner with full administrative control.
     * Can manage all aspects including deletion and transfers.
     */
    OWNER,
    
    /**
     * Administrator with broad management capabilities.
     * Can manage members, settings, and configurations.
     */
    ADMIN,
    
    /**
     * Standard member with regular access.
     * Can use organization resources based on assigned permissions.
     */
    MEMBER,
    
    /**
     * Guest with limited access.
     * Temporary or restricted access to specific resources.
     */
    GUEST
}
