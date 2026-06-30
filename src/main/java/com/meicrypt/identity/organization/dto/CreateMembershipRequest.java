package com.meicrypt.identity.organization.dto;

import com.meicrypt.identity.organization.entity.MembershipRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a new organization membership.
 * Used when manually adding a user to an organization.
 */
public record CreateMembershipRequest(
    @NotNull(message = "Organization ID is required")
    UUID organizationId,
    
    @NotNull(message = "User ID is required")
    UUID userId,
    
    @NotNull(message = "Role is required")
    MembershipRole role
) {
}
