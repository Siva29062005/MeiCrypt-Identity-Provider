package com.meicrypt.identity.mfa.dto;

import com.meicrypt.identity.mfa.entity.MfaFactorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Exchange a pending {@code challengeToken} plus a proof from any registered
 * factor for a full Phase-3 token pair.
 */
public record VerifyMfaChallengeRequest(
        @NotBlank(message = "challengeToken is required")
        String challengeToken,

        @NotNull(message = "factorType is required")
        MfaFactorType factorType,

        /**
         * TOTP: a 6-10 digit code. WebAuthn: JSON string containing the
         * PublicKeyCredential returned by navigator.credentials.get().
         */
        @NotBlank(message = "proof is required")
        String proof
) {}
