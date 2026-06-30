package com.meicrypt.identity.organization.controller;

import com.meicrypt.identity.organization.dto.OrganizationSettingsDTO;
import com.meicrypt.identity.organization.dto.UpdateOrganizationSettingsRequest;
import com.meicrypt.identity.organization.service.OrganizationSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for OrganizationSettings management.
 * Provides endpoints for managing organization settings.
 * 
 * Base path: /api/v1/organizations/{organizationId}/settings
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/settings")
@Tag(name = "Organization Settings", description = "Organization settings management endpoints")
public class OrganizationSettingsController {

    private final OrganizationSettingsService settingsService;

    /**
     * Constructor injection - zero field injection principle.
     *
     * @param settingsService The organization settings service
     */
    public OrganizationSettingsController(OrganizationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @Operation(
        summary = "Get organization settings", 
        description = "Retrieves settings for the specified organization. Creates default settings if none exist."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Settings retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<OrganizationSettingsDTO> getOrganizationSettings(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID organizationId) {
        OrganizationSettingsDTO settings = settingsService.getOrganizationSettings(organizationId);
        return ResponseEntity.ok(settings);
    }

    @PutMapping
    @Operation(
        summary = "Update organization settings", 
        description = "Updates settings for the specified organization. Only provided fields are updated."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Settings updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<OrganizationSettingsDTO> updateOrganizationSettings(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID organizationId,
            @Valid @RequestBody UpdateOrganizationSettingsRequest request) {
        OrganizationSettingsDTO updatedSettings = 
                settingsService.updateOrganizationSettings(organizationId, request);
        return ResponseEntity.ok(updatedSettings);
    }

    @PostMapping("/reset")
    @Operation(
        summary = "Reset organization settings", 
        description = "Resets settings to default values for the specified organization"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Settings reset successfully"),
        @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<OrganizationSettingsDTO> resetOrganizationSettings(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID organizationId) {
        OrganizationSettingsDTO resetSettings = settingsService.resetOrganizationSettings(organizationId);
        return ResponseEntity.ok(resetSettings);
    }
}
