package com.meicrypt.identity.organization.controller;

import com.meicrypt.identity.organization.dto.CreateCustomDomainRequest;
import com.meicrypt.identity.organization.dto.DomainVerificationInfoDTO;
import com.meicrypt.identity.organization.dto.OrganizationCustomDomainDTO;
import com.meicrypt.identity.organization.service.OrganizationCustomDomainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for organization custom domain management.
 * Provides endpoints for managing custom domains and domain verification.
 */
@RestController
@RequestMapping("/api/v1/organizations/domains")
@Tag(name = "Organization Custom Domains", description = "Custom domain management endpoints")
public class OrganizationCustomDomainController {

    private final OrganizationCustomDomainService customDomainService;

    public OrganizationCustomDomainController(OrganizationCustomDomainService customDomainService) {
        this.customDomainService = customDomainService;
    }

    /**
     * Create a new custom domain.
     *
     * @param request The custom domain creation request
     * @return The created custom domain
     */
    @PostMapping
    @Operation(summary = "Create a new custom domain")
    public ResponseEntity<OrganizationCustomDomainDTO> createCustomDomain(
            @Valid @RequestBody CreateCustomDomainRequest request) {
        OrganizationCustomDomainDTO customDomain = customDomainService.createCustomDomain(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(customDomain);
    }

    /**
     * Get a custom domain by ID.
     *
     * @param domainId The custom domain ID
     * @return The custom domain details
     */
    @GetMapping("/{domainId}")
    @Operation(summary = "Get custom domain by ID")
    public ResponseEntity<OrganizationCustomDomainDTO> getCustomDomainById(
            @PathVariable UUID domainId) {
        OrganizationCustomDomainDTO customDomain = customDomainService.getCustomDomainById(domainId);
        return ResponseEntity.ok(customDomain);
    }

    /**
     * Get all custom domains for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of custom domains
     */
    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get all custom domains for an organization")
    public ResponseEntity<List<OrganizationCustomDomainDTO>> getCustomDomainsByOrganization(
            @PathVariable UUID organizationId) {
        List<OrganizationCustomDomainDTO> customDomains = 
            customDomainService.getCustomDomainsByOrganization(organizationId);
        return ResponseEntity.ok(customDomains);
    }

    /**
     * Get verified custom domains for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of verified custom domains
     */
    @GetMapping("/organization/{organizationId}/verified")
    @Operation(summary = "Get verified custom domains for an organization")
    public ResponseEntity<List<OrganizationCustomDomainDTO>> getVerifiedCustomDomainsByOrganization(
            @PathVariable UUID organizationId) {
        List<OrganizationCustomDomainDTO> customDomains = 
            customDomainService.getVerifiedCustomDomainsByOrganization(organizationId);
        return ResponseEntity.ok(customDomains);
    }

    /**
     * Get verification information for a custom domain.
     *
     * @param domainId The custom domain ID
     * @return The verification info with instructions
     */
    @GetMapping("/{domainId}/verification-info")
    @Operation(summary = "Get verification information for a domain")
    public ResponseEntity<DomainVerificationInfoDTO> getVerificationInfo(
            @PathVariable UUID domainId) {
        DomainVerificationInfoDTO verificationInfo = 
            customDomainService.getVerificationInfo(domainId);
        return ResponseEntity.ok(verificationInfo);
    }

    /**
     * Verify a custom domain.
     *
     * @param domainId The custom domain ID
     * @return The verified custom domain
     */
    @PostMapping("/{domainId}/verify")
    @Operation(summary = "Verify a custom domain")
    public ResponseEntity<OrganizationCustomDomainDTO> verifyCustomDomain(
            @PathVariable UUID domainId) {
        OrganizationCustomDomainDTO customDomain = customDomainService.verifyCustomDomain(domainId);
        return ResponseEntity.ok(customDomain);
    }

    /**
     * Set a custom domain as primary.
     *
     * @param domainId The custom domain ID
     * @return The updated custom domain
     */
    @PutMapping("/{domainId}/set-primary")
    @Operation(summary = "Set a custom domain as primary")
    public ResponseEntity<OrganizationCustomDomainDTO> setPrimaryDomain(
            @PathVariable UUID domainId) {
        OrganizationCustomDomainDTO customDomain = customDomainService.setPrimaryDomain(domainId);
        return ResponseEntity.ok(customDomain);
    }

    /**
     * Delete a custom domain.
     *
     * @param domainId The custom domain ID
     * @return No content response
     */
    @DeleteMapping("/{domainId}")
    @Operation(summary = "Delete a custom domain")
    public ResponseEntity<Void> deleteCustomDomain(@PathVariable UUID domainId) {
        customDomainService.deleteCustomDomain(domainId);
        return ResponseEntity.noContent().build();
    }
}
