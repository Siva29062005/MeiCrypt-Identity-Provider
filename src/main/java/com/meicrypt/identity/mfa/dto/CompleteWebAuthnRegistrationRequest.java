package com.meicrypt.identity.mfa.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/**
 * Second step of the WebAuthn registration ceremony (Module 9.2). Carries the
 * base64url-encoded attestation payload returned by
 * {@code navigator.credentials.create()}.
 */
public record CompleteWebAuthnRegistrationRequest(
        @NotBlank(message = "factorId is required")
        UUID factorId,

        @NotBlank(message = "credentialId is required")
        String credentialId,

        @NotBlank(message = "clientDataJson is required")
        String clientDataJsonBase64,

        @NotBlank(message = "attestationObject is required")
        String attestationObjectBase64,

        List<String> transports
) {}
