package com.meicrypt.identity.organization.controller;

import com.meicrypt.identity.organization.dto.CreateOrganizationRequest;
import com.meicrypt.identity.organization.dto.OrganizationDTO;
import com.meicrypt.identity.organization.dto.UpdateOrganizationRequest;
import com.meicrypt.identity.organization.entity.OrganizationStatus;
import com.meicrypt.identity.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Organization management.
 * Provides endpoints for CRUD operations on organizations.
 * 
 * Base path: /api/v1/organizations
 */
@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Organization management endpoints - Multi-tenant anchor")
public class OrganizationController {

    private final OrganizationService organizationService;

    /**
     * Constructor injection - zero field injection principle.
     *
     * @param organizationService The organization service
     */
    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @Operation(summary = "Create a new organization", description = "Creates a new organization with the provided details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Organization created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "409", description = "Organization with slug already exists")
    })
    public ResponseEntity<OrganizationDTO> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationDTO createdOrganization = organizationService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdOrganization);
    }

    @GetMapping
    @Operation(summary = "Get all organizations", description = "Retrieves a list of all organizations")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved organizations")
    public ResponseEntity<List<OrganizationDTO>> getAllOrganizations(
            @Parameter(description = "Filter by organization status")
            @RequestParam(required = false) OrganizationStatus status,
            
            @Parameter(description = "Search by organization name")
            @RequestParam(required = false) String name) {
        
        List<OrganizationDTO> organizations;
        
        if (status != null) {
            organizations = organizationService.getOrganizationsByStatus(status);
        } else if (name != null && !name.isBlank()) {
            organizations = organizationService.searchOrganizationsByName(name);
        } else {
            organizations = organizationService.getAllOrganizations();
        }
        
        return ResponseEntity.ok(organizations);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get organization by ID", description = "Retrieves a specific organization by its UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Organization found"),
        @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<OrganizationDTO> getOrganizationById(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID id) {
        OrganizationDTO organization = organizationService.getOrganizationById(id);
        return ResponseEntity.ok(organization);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get organization by slug", description = "Retrieves a specific organization by its unique slug")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Organization found"),
        @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<OrganizationDTO> getOrganizationBySlug(
            @Parameter(description = "Organization slug", required = true)
            @PathVariable String slug) {
        OrganizationDTO organization = organizationService.getOrganizationBySlug(slug);
        return ResponseEntity.ok(organization);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update organization", description = "Updates an existing organization")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Organization updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<OrganizationDTO> updateOrganization(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        OrganizationDTO updatedOrganization = organizationService.updateOrganization(id, request);
        return ResponseEntity.ok(updatedOrganization);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete organization", description = "Deletes an organization by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Organization deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<Void> deleteOrganization(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend organization", description = "Suspends an organization (changes status to SUSPENDED)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Organization suspended successfully"),
        @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<OrganizationDTO> suspendOrganization(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID id) {
        OrganizationDTO suspendedOrganization = organizationService.suspendOrganization(id);
        return ResponseEntity.ok(suspendedOrganization);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate organization", description = "Activates an organization (changes status to ACTIVE)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Organization activated successfully"),
        @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<OrganizationDTO> activateOrganization(
            @Parameter(description = "Organization UUID", required = true)
            @PathVariable UUID id) {
        OrganizationDTO activatedOrganization = organizationService.activateOrganization(id);
        return ResponseEntity.ok(activatedOrganization);
    }
}
