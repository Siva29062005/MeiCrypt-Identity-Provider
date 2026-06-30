package com.meicrypt.identity.organization.entity;

/**
 * Enum defining the status of an organization invitation.
 * Tracks the lifecycle of invitation tokens.
 */
public enum InvitationStatus {
    /**
     * Invitation has been sent and is awaiting acceptance.
     */
    PENDING,
    
    /**
     * Invitation has been accepted and membership created.
     */
    ACCEPTED,
    
    /**
     * Invitation has expired past its validity period.
     */
    EXPIRED,
    
    /**
     * Invitation has been manually revoked by an administrator.
     */
    REVOKED
}
