package com.meicrypt.identity.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for email verification.
 */
public record VerifyEmailRequest(
    @NotBlank(message = "Verification token is required")
    String token
) {
}
