package com.meicrypt.identity.organization.dto;

import com.meicrypt.identity.organization.entity.MembershipRole;
import com.meicrypt.identity.organization.entity.MembershipStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating an organization membership.
 * Allows changing role and status of existing memberships.
 */
public record UpdateMembershipRequest(
    @NotNull(message = "Role is required")
    MembershipRole role,
    
    @NotNull(message = "Status is required")
    MembershipStatus status
) {
}
