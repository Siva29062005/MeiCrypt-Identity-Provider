package com.meicrypt.identity.organization.dto;

import com.meicrypt.identity.organization.entity.MembershipRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a new organization invitation.
 * Used when inviting a user to join an organization.
 */
public record CreateInvitationRequest(
    @NotNull(message = "Organization ID is required")
    UUID organizationId,
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,
    
    @NotNull(message = "Role is required")
    MembershipRole role,
    
    UUID invitedByUserId
) {
}
