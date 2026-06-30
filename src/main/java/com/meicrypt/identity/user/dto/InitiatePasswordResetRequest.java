package com.meicrypt.identity.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for initiating password reset.
 */
public record InitiatePasswordResetRequest(
    @NotNull(message = "Organization ID is required")
    UUID organizationId,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email
) {
}
