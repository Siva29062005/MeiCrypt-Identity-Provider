package com.meicrypt.identity.organization.controller;

import com.meicrypt.identity.organization.dto.AcceptInvitationRequest;
import com.meicrypt.identity.organization.dto.CreateInvitationRequest;
import com.meicrypt.identity.organization.dto.OrganizationInvitationDTO;
import com.meicrypt.identity.organization.service.OrganizationInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for organization invitation management.
 * Provides endpoints for creating, accepting, and managing invitations.
 */
@RestController
@RequestMapping("/api/v1/organizations/invitations")
@Tag(name = "Organization Invitations", description = "Organization invitation management endpoints")
public class OrganizationInvitationController {

    private final OrganizationInvitationService invitationService;

    public OrganizationInvitationController(OrganizationInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /**
     * Create a new organization invitation.
     *
     * @param request The invitation creation request
     * @return The created invitation
     */
    @PostMapping
    @Operation(summary = "Create a new organization invitation")
    public ResponseEntity<OrganizationInvitationDTO> createInvitation(
            @Valid @RequestBody CreateInvitationRequest request) {
        OrganizationInvitationDTO invitation = invitationService.createInvitation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
    }

    /**
     * Accept an invitation and create membership.
     *
     * @param request The accept invitation request
     * @return The updated invitation
     */
    @PostMapping("/accept")
    @Operation(summary = "Accept an organization invitation")
    public ResponseEntity<OrganizationInvitationDTO> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request) {
        OrganizationInvitationDTO invitation = invitationService.acceptInvitation(request);
        return ResponseEntity.ok(invitation);
    }

    /**
     * Get an invitation by ID.
     *
     * @param invitationId The invitation ID
     * @return The invitation details
     */
    @GetMapping("/{invitationId}")
    @Operation(summary = "Get invitation by ID")
    public ResponseEntity<OrganizationInvitationDTO> getInvitationById(
            @PathVariable UUID invitationId) {
        OrganizationInvitationDTO invitation = invitationService.getInvitationById(invitationId);
        return ResponseEntity.ok(invitation);
    }

    /**
     * Get all invitations for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of invitations
     */
    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get all invitations for an organization")
    public ResponseEntity<List<OrganizationInvitationDTO>> getInvitationsByOrganization(
            @PathVariable UUID organizationId) {
        List<OrganizationInvitationDTO> invitations = 
            invitationService.getInvitationsByOrganization(organizationId);
        return ResponseEntity.ok(invitations);
    }

    /**
     * Get pending invitations for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of pending invitations
     */
    @GetMapping("/organization/{organizationId}/pending")
    @Operation(summary = "Get pending invitations for an organization")
    public ResponseEntity<List<OrganizationInvitationDTO>> getPendingInvitationsByOrganization(
            @PathVariable UUID organizationId) {
        List<OrganizationInvitationDTO> invitations = 
            invitationService.getPendingInvitationsByOrganization(organizationId);
        return ResponseEntity.ok(invitations);
    }

    /**
     * Get all invitations for a specific email address.
     *
     * @param email The email address
     * @return List of invitations
     */
    @GetMapping("/email/{email}")
    @Operation(summary = "Get invitations for an email address")
    public ResponseEntity<List<OrganizationInvitationDTO>> getInvitationsByEmail(
            @PathVariable String email) {
        List<OrganizationInvitationDTO> invitations = 
            invitationService.getInvitationsByEmail(email);
        return ResponseEntity.ok(invitations);
    }

    /**
     * Revoke an invitation.
     *
     * @param invitationId The invitation ID
     * @return No content response
     */
    @DeleteMapping("/{invitationId}")
    @Operation(summary = "Revoke an invitation")
    public ResponseEntity<Void> revokeInvitation(@PathVariable UUID invitationId) {
        invitationService.revokeInvitation(invitationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Mark expired invitations (admin endpoint).
     *
     * @return Count of invitations marked as expired
     */
    @PostMapping("/mark-expired")
    @Operation(summary = "Mark expired invitations (admin)")
    public ResponseEntity<Integer> markExpiredInvitations() {
        int count = invitationService.markExpiredInvitations();
        return ResponseEntity.ok(count);
    }
}
