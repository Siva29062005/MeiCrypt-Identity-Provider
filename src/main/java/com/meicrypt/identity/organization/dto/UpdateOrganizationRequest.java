package com.meicrypt.identity.organization.dto;

import com.meicrypt.identity.organization.entity.OrganizationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing organization.
 * All fields are optional - only provided fields will be updated.
 */
public record UpdateOrganizationRequest(
        
        @NotBlank(message = "Organization name cannot be blank if provided")
        @Size(min = 2, max = 255, message = "Organization name must be between 2 and 255 characters")
        String name,
        
        OrganizationStatus status
) {
}
