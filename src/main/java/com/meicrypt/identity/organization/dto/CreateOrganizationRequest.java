package com.meicrypt.identity.organization.dto;

import com.meicrypt.identity.common.validation.ValidSlug;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new organization.
 * Contains validation rules for organization creation.
 */
public record CreateOrganizationRequest(
        
        @NotBlank(message = "Organization name is required")
        @Size(min = 2, max = 255, message = "Organization name must be between 2 and 255 characters")
        String name,
        
        @NotBlank(message = "Organization slug is required")
        @ValidSlug(minLength = 3, maxLength = 100)
        String slug
) {
}
