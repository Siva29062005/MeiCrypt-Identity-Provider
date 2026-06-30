package com.meicrypt.identity.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for accepting an organization invitation.
 * Used when a user accepts an invitation and creates their membership.
 */
public record AcceptInvitationRequest(
    @NotBlank(message = "Invitation token is required")
    String invitationToken,
    
    @NotNull(message = "User ID is required")
    UUID userId
) {
}
