package com.meicrypt.identity.mfa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyTotpEnrollmentRequest(
        @NotBlank(message = "code is required")
        @Pattern(regexp = "\\d{6,10}", message = "code must be 6-10 digits")
        String code
) {}
