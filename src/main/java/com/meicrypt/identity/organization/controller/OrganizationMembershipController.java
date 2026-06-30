package com.meicrypt.identity.organization.controller;

import com.meicrypt.identity.organization.dto.CreateMembershipRequest;
import com.meicrypt.identity.organization.dto.OrganizationMembershipDTO;
import com.meicrypt.identity.organization.dto.UpdateMembershipRequest;
import com.meicrypt.identity.organization.service.OrganizationMembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for organization membership management.
 * Provides endpoints for managing user memberships within organizations.
 */
@RestController
@RequestMapping("/api/v1/organizations/memberships")
@Tag(name = "Organization Memberships", description = "Organization membership management endpoints")
public class OrganizationMembershipController {

    private final OrganizationMembershipService membershipService;

    public OrganizationMembershipController(OrganizationMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    /**
     * Create a new organization membership.
     *
     * @param request The membership creation request
     * @return The created membership
     */
    @PostMapping
    @Operation(summary = "Create a new organization membership")
    public ResponseEntity<OrganizationMembershipDTO> createMembership(
            @Valid @RequestBody CreateMembershipRequest request) {
        OrganizationMembershipDTO membership = membershipService.createMembership(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(membership);
    }

    /**
     * Get a specific membership by ID.
     *
     * @param membershipId The membership ID
     * @return The membership details
     */
    @GetMapping("/{membershipId}")
    @Operation(summary = "Get membership by ID")
    public ResponseEntity<OrganizationMembershipDTO> getMembershipById(
            @PathVariable UUID membershipId) {
        OrganizationMembershipDTO membership = membershipService.getMembershipById(membershipId);
        return ResponseEntity.ok(membership);
    }

    /**
     * Get all memberships for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of memberships
     */
    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get all memberships for an organization")
    public ResponseEntity<List<OrganizationMembershipDTO>> getMembershipsByOrganization(
            @PathVariable UUID organizationId) {
        List<OrganizationMembershipDTO> memberships = 
            membershipService.getMembershipsByOrganization(organizationId);
        return ResponseEntity.ok(memberships);
    }

    /**
     * Get active memberships for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of active memberships
     */
    @GetMapping("/organization/{organizationId}/active")
    @Operation(summary = "Get active memberships for an organization")
    public ResponseEntity<List<OrganizationMembershipDTO>> getActiveMembershipsByOrganization(
            @PathVariable UUID organizationId) {
        List<OrganizationMembershipDTO> memberships = 
            membershipService.getActiveMembershipsByOrganization(organizationId);
        return ResponseEntity.ok(memberships);
    }

    /**
     * Get all memberships for a specific user.
     *
     * @param userId The user ID
     * @return List of memberships
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all memberships for a user")
    public ResponseEntity<List<OrganizationMembershipDTO>> getMembershipsByUser(
            @PathVariable UUID userId) {
        List<OrganizationMembershipDTO> memberships = 
            membershipService.getMembershipsByUser(userId);
        return ResponseEntity.ok(memberships);
    }

    /**
     * Get a specific membership by organization and user.
     *
     * @param organizationId The organization ID
     * @param userId         The user ID
     * @return The membership details
     */
    @GetMapping("/organization/{organizationId}/user/{userId}")
    @Operation(summary = "Get membership by organization and user")
    public ResponseEntity<OrganizationMembershipDTO> getMembershipByOrganizationAndUser(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId) {
        OrganizationMembershipDTO membership = 
            membershipService.getMembershipByOrganizationAndUser(organizationId, userId);
        return ResponseEntity.ok(membership);
    }

    /**
     * Update an existing membership.
     *
     * @param membershipId The membership ID
     * @param request      The update request
     * @return The updated membership
     */
    @PutMapping("/{membershipId}")
    @Operation(summary = "Update a membership")
    public ResponseEntity<OrganizationMembershipDTO> updateMembership(
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateMembershipRequest request) {
        OrganizationMembershipDTO membership = 
            membershipService.updateMembership(membershipId, request);
        return ResponseEntity.ok(membership);
    }

    /**
     * Delete a membership.
     *
     * @param membershipId The membership ID
     * @return No content response
     */
    @DeleteMapping("/{membershipId}")
    @Operation(summary = "Delete a membership")
    public ResponseEntity<Void> deleteMembership(@PathVariable UUID membershipId) {
        membershipService.deleteMembership(membershipId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Count active memberships in an organization.
     *
     * @param organizationId The organization ID
     * @return The count of active memberships
     */
    @GetMapping("/organization/{organizationId}/count")
    @Operation(summary = "Count active memberships in an organization")
    public ResponseEntity<Long> countActiveMemberships(@PathVariable UUID organizationId) {
        long count = membershipService.countActiveMemberships(organizationId);
        return ResponseEntity.ok(count);
    }
}
