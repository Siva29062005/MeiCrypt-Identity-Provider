package com.meicrypt.identity.mfa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnrollTotpRequest(
        @NotBlank(message = "displayName is required")
        @Size(max = 120)
        String displayName
) {}
