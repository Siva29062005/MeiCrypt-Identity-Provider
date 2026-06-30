package com.meicrypt.identity.organization.service;

import com.meicrypt.identity.common.exception.DuplicateResourceException;
import com.meicrypt.identity.common.exception.InvalidOperationException;
import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import com.meicrypt.identity.organization.dto.AcceptInvitationRequest;
import com.meicrypt.identity.organization.dto.CreateInvitationRequest;
import com.meicrypt.identity.organization.dto.OrganizationInvitationDTO;
import com.meicrypt.identity.organization.entity.InvitationStatus;
import com.meicrypt.identity.organization.entity.MembershipStatus;
import com.meicrypt.identity.organization.entity.OrganizationInvitation;
import com.meicrypt.identity.organization.entity.OrganizationMembership;
import com.meicrypt.identity.organization.mapper.OrganizationInvitationMapper;
import com.meicrypt.identity.organization.repository.OrganizationInvitationRepository;
import com.meicrypt.identity.organization.repository.OrganizationMembershipRepository;
import com.meicrypt.identity.organization.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing organization invitations.
 * Handles secure multi-stage invitation workflows with token generation and expiry.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationInvitationService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationInvitationService.class);
    private static final int DEFAULT_INVITATION_EXPIRY_DAYS = 7;
    private static final int TOKEN_BYTE_LENGTH = 32;
    
    private final OrganizationInvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationInvitationMapper invitationMapper;
    private final SecureRandom secureRandom;

    public OrganizationInvitationService(
            OrganizationInvitationRepository invitationRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationInvitationMapper invitationMapper) {
        this.invitationRepository = invitationRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.invitationMapper = invitationMapper;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Create a new organization invitation.
     *
     * @param request The invitation creation request
     * @return The created invitation DTO
     * @throws ResourceNotFoundException if organization doesn't exist
     * @throws DuplicateResourceException if pending invitation or membership already exists
     */
    @Transactional
    public OrganizationInvitationDTO createInvitation(CreateInvitationRequest request) {
        logger.info("Creating invitation for email {} to organization {}", 
                   request.email(), request.organizationId());

        // Verify organization exists
        if (!organizationRepository.existsById(request.organizationId())) {
            throw new ResourceNotFoundException("Organization", request.organizationId().toString());
        }

        // Check if user already has membership
        // Note: In Phase 2, we'll add User entity and validate userId exists
        // For now, we just check if a pending invitation exists
        if (invitationRepository.existsByOrganizationIdAndEmailAndStatus(
                request.organizationId(), request.email(), InvitationStatus.PENDING)) {
            throw new DuplicateResourceException(
                "Pending invitation already exists for email " + request.email() + 
                " in organization " + request.organizationId(), "email");
        }

        // Generate secure invitation token
        String invitationToken = generateSecureToken();
        
        // Calculate expiration time
        Instant expiresAt = Instant.now().plus(DEFAULT_INVITATION_EXPIRY_DAYS, ChronoUnit.DAYS);

        // Create new invitation
        OrganizationInvitation invitation = new OrganizationInvitation(
            request.organizationId(),
            request.email(),
            request.invitedByUserId(),
            invitationToken,
            request.role(),
            expiresAt
        );

        OrganizationInvitation savedInvitation = invitationRepository.save(invitation);
        logger.info("Successfully created invitation with id: {} (expires: {})", 
                   savedInvitation.getId(), expiresAt);

        // TODO: In Phase 10, send email notification with invitation link
        // notificationService.sendInvitationEmail(savedInvitation);

        return invitationMapper.toDTO(savedInvitation);
    }

    /**
     * Accept an invitation and create a membership.
     *
     * @param request The accept invitation request
     * @return The created membership DTO (wrapped in invitation DTO for now)
     * @throws ResourceNotFoundException if invitation not found
     * @throws InvalidOperationException if invitation is not valid
     */
    @Transactional
    public OrganizationInvitationDTO acceptInvitation(AcceptInvitationRequest request) {
        logger.info("Processing invitation acceptance for token: {}", request.invitationToken());

        // Find invitation by token
        OrganizationInvitation invitation = invitationRepository
            .findByInvitationToken(request.invitationToken())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Invitation", request.invitationToken(), 
                "Invitation not found with token: " + request.invitationToken()));

        // Validate invitation status
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvalidOperationException(
                "Invitation is not pending (status: " + invitation.getStatus() + ")");
        }

        // Check if invitation is expired
        if (invitation.isExpired()) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new InvalidOperationException("Invitation has expired");
        }

        // Check if membership already exists
        if (membershipRepository.existsByOrganizationIdAndUserId(
                invitation.getOrganizationId(), request.userId())) {
            throw new DuplicateResourceException(
                "Membership already exists for user in this organization", "user_id");
        }

        // Create membership
        OrganizationMembership membership = new OrganizationMembership(
            invitation.getOrganizationId(),
            request.userId(),
            invitation.getRole(),
            MembershipStatus.ACTIVE
        );
        membershipRepository.save(membership);

        // Update invitation status
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        OrganizationInvitation updatedInvitation = invitationRepository.save(invitation);

        logger.info("Successfully accepted invitation {} and created membership for user {}", 
                   invitation.getId(), request.userId());

        return invitationMapper.toDTO(updatedInvitation);
    }

    /**
     * Get an invitation by ID.
     *
     * @param invitationId The invitation ID
     * @return The invitation DTO
     * @throws ResourceNotFoundException if invitation not found
     */
    public OrganizationInvitationDTO getInvitationById(UUID invitationId) {
        logger.debug("Fetching invitation with id: {}", invitationId);

        OrganizationInvitation invitation = invitationRepository.findById(invitationId)
            .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId.toString()));

        return invitationMapper.toDTO(invitation);
    }

    /**
     * Get all invitations for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of invitation DTOs
     */
    public List<OrganizationInvitationDTO> getInvitationsByOrganization(UUID organizationId) {
        logger.debug("Fetching invitations for organization: {}", organizationId);

        List<OrganizationInvitation> invitations = 
            invitationRepository.findByOrganizationId(organizationId);

        return invitations.stream()
            .map(invitationMapper::toDTO)
            .toList();
    }

    /**
     * Get pending invitations for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of pending invitation DTOs
     */
    public List<OrganizationInvitationDTO> getPendingInvitationsByOrganization(
            UUID organizationId) {
        logger.debug("Fetching pending invitations for organization: {}", organizationId);

        List<OrganizationInvitation> invitations = 
            invitationRepository.findByOrganizationIdAndStatus(
                organizationId, InvitationStatus.PENDING);

        return invitations.stream()
            .map(invitationMapper::toDTO)
            .toList();
    }

    /**
     * Get all invitations for a specific email address.
     *
     * @param email The email address
     * @return List of invitation DTOs
     */
    public List<OrganizationInvitationDTO> getInvitationsByEmail(String email) {
        logger.debug("Fetching invitations for email: {}", email);

        List<OrganizationInvitation> invitations = 
            invitationRepository.findByEmail(email);

        return invitations.stream()
            .map(invitationMapper::toDTO)
            .toList();
    }

    /**
     * Revoke an invitation.
     *
     * @param invitationId The invitation ID
     * @throws ResourceNotFoundException if invitation not found
     * @throws InvalidOperationException if invitation cannot be revoked
     */
    @Transactional
    public void revokeInvitation(UUID invitationId) {
        logger.info("Revoking invitation with id: {}", invitationId);

        OrganizationInvitation invitation = invitationRepository.findById(invitationId)
            .orElseThrow(() -> new ResourceNotFoundException("Invitation", invitationId.toString()));

        // Only pending invitations can be revoked
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvalidOperationException(
                "Only pending invitations can be revoked (current status: " + 
                invitation.getStatus() + ")");
        }

        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);

        logger.info("Successfully revoked invitation with id: {}", invitationId);
    }

    /**
     * Mark expired invitations as EXPIRED status.
     * This should be called periodically by a scheduled task.
     *
     * @return Number of invitations marked as expired
     */
    @Transactional
    public int markExpiredInvitations() {
        logger.debug("Checking for expired invitations");

        List<OrganizationInvitation> expiredInvitations = 
            invitationRepository.findByStatusAndExpiresAtBefore(
                InvitationStatus.PENDING, Instant.now());

        int count = 0;
        for (OrganizationInvitation invitation : expiredInvitations) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            count++;
        }

        if (count > 0) {
            logger.info("Marked {} invitations as expired", count);
        }

        return count;
    }

    /**
     * Generate a secure random token for invitations.
     *
     * @return Base64-encoded secure random token
     */
    private String generateSecureToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
