package com.meicrypt.identity.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for /auth/logout. The refresh token is required so we can
 * terminate the exact session bound to it.
 */
public record LogoutRequest(
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {}
