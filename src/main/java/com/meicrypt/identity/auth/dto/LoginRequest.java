package com.meicrypt.identity.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload for username/password login.
 */
public record LoginRequest(
        @NotNull(message = "organizationId is required")
        UUID organizationId,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @NotBlank(message = "password is required")
        String password,

        String deviceFingerprint,
        String deviceName
) {}
