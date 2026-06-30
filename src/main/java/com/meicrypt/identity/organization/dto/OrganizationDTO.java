package com.meicrypt.identity.organization.dto;

import com.meicrypt.identity.organization.entity.OrganizationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for Organization.
 * Used for API responses to expose organization data.
 */
public record OrganizationDTO(
        UUID id,
        
        @NotBlank(message = "Organization name is required")
        @Size(min = 2, max = 255, message = "Organization name must be between 2 and 255 characters")
        String name,
        
        String slug,
        
        @NotNull(message = "Organization status is required")
        OrganizationStatus status,
        
        Instant createdAt,
        
        Instant updatedAt
) {
}
