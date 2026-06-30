package com.meicrypt.identity.organization.dto;

import com.meicrypt.identity.organization.entity.DomainVerificationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Request DTO for creating a new custom domain.
 * Used when adding a custom domain to an organization.
 */
public record CreateCustomDomainRequest(
    @NotNull(message = "Organization ID is required")
    UUID organizationId,
    
    @NotBlank(message = "Domain is required")
    @Pattern(regexp = "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$",
             message = "Invalid domain format")
    String domain,
    
    @NotNull(message = "Verification method is required")
    DomainVerificationMethod verificationMethod
) {
}
