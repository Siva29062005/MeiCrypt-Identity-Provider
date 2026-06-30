package com.meicrypt.identity.organization.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for OrganizationSettings.
 * Used for API responses to expose organization settings data.
 */
public record OrganizationSettingsDTO(
        UUID id,
        UUID organizationId,
        
        // Brand customization
        String brandName,
        String brandLogoUrl,
        
        // Localization
        String primaryTimezone,
        String primaryLanguage,
        
        // Password policies
        Integer passwordMinLength,
        Boolean passwordRequireUppercase,
        Boolean passwordRequireLowercase,
        Boolean passwordRequireNumbers,
        Boolean passwordRequireSpecialChars,
        
        // Session management
        Integer maxSessionDurationMinutes,
        
        Instant createdAt,
        Instant updatedAt
) {
}
