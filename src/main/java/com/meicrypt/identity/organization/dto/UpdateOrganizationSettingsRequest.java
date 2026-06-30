package com.meicrypt.identity.organization.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating organization settings.
 * All fields are optional - only provided fields will be updated.
 */
public record UpdateOrganizationSettingsRequest(
        
        // Brand customization
        @Size(max = 255, message = "Brand name must not exceed 255 characters")
        String brandName,
        
        @Size(max = 500, message = "Brand logo URL must not exceed 500 characters")
        String brandLogoUrl,
        
        // Localization
        @Size(max = 50, message = "Timezone must not exceed 50 characters")
        String primaryTimezone,
        
        @Size(min = 2, max = 10, message = "Language code must be between 2 and 10 characters")
        String primaryLanguage,
        
        // Password policies
        @Min(value = 8, message = "Password minimum length must be at least 8")
        @Max(value = 128, message = "Password minimum length must not exceed 128")
        Integer passwordMinLength,
        
        Boolean passwordRequireUppercase,
        Boolean passwordRequireLowercase,
        Boolean passwordRequireNumbers,
        Boolean passwordRequireSpecialChars,
        
        // Session management
        @Min(value = 5, message = "Max session duration must be at least 5 minutes")
        @Max(value = 43200, message = "Max session duration must not exceed 43200 minutes (30 days)")
        Integer maxSessionDurationMinutes
) {
}
