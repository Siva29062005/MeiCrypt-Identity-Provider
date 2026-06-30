package com.meicrypt.identity.organization.service;

import com.meicrypt.identity.common.exception.DuplicateResourceException;
import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import com.meicrypt.identity.organization.dto.CreateMembershipRequest;
import com.meicrypt.identity.organization.dto.OrganizationMembershipDTO;
import com.meicrypt.identity.organization.dto.UpdateMembershipRequest;
import com.meicrypt.identity.organization.entity.MembershipStatus;
import com.meicrypt.identity.organization.entity.OrganizationMembership;
import com.meicrypt.identity.organization.mapper.OrganizationMembershipMapper;
import com.meicrypt.identity.organization.repository.OrganizationMembershipRepository;
import com.meicrypt.identity.organization.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing organization memberships.
 * Handles business logic for linking users to organizations with specific roles.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationMembershipService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationMembershipService.class);

    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipMapper membershipMapper;

    public OrganizationMembershipService(
            OrganizationMembershipRepository membershipRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipMapper membershipMapper) {
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.membershipMapper = membershipMapper;
    }

    /**
     * Create a new organization membership.
     *
     * @param request The membership creation request
     * @return The created membership DTO
     * @throws ResourceNotFoundException if organization doesn't exist
     * @throws DuplicateResourceException if membership already exists
     */
    @Transactional
    public OrganizationMembershipDTO createMembership(CreateMembershipRequest request) {
        logger.info("Creating membership for user {} in organization {}", 
                   request.userId(), request.organizationId());

        // Verify organization exists
        if (!organizationRepository.existsById(request.organizationId())) {
            throw new ResourceNotFoundException("Organization", request.organizationId().toString());
        }

        // Check if membership already exists
        if (membershipRepository.existsByOrganizationIdAndUserId(
                request.organizationId(), request.userId())) {
            throw new DuplicateResourceException(
                "Membership already exists for user " + request.userId() + 
                " in organization " + request.organizationId(), "user_id");
        }

        // Create new membership
        OrganizationMembership membership = new OrganizationMembership(
            request.organizationId(),
            request.userId(),
            request.role(),
            MembershipStatus.ACTIVE
        );

        OrganizationMembership savedMembership = membershipRepository.save(membership);
        logger.info("Successfully created membership with id: {}", savedMembership.getId());

        return membershipMapper.toDTO(savedMembership);
    }

    /**
     * Get a specific membership by ID.
     *
     * @param membershipId The membership ID
     * @return The membership DTO
     * @throws ResourceNotFoundException if membership not found
     */
    public OrganizationMembershipDTO getMembershipById(UUID membershipId) {
        logger.debug("Fetching membership with id: {}", membershipId);

        OrganizationMembership membership = membershipRepository.findById(membershipId)
            .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId.toString()));

        return membershipMapper.toDTO(membership);
    }

    /**
     * Get all memberships for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of membership DTOs
     */
    public List<OrganizationMembershipDTO> getMembershipsByOrganization(UUID organizationId) {
        logger.debug("Fetching memberships for organization: {}", organizationId);

        List<OrganizationMembership> memberships = 
            membershipRepository.findByOrganizationId(organizationId);

        return memberships.stream()
            .map(membershipMapper::toDTO)
            .toList();
    }

    /**
     * Get all memberships for a specific user.
     *
     * @param userId The user ID
     * @return List of membership DTOs
     */
    public List<OrganizationMembershipDTO> getMembershipsByUser(UUID userId) {
        logger.debug("Fetching memberships for user: {}", userId);

        List<OrganizationMembership> memberships = 
            membershipRepository.findByUserId(userId);

        return memberships.stream()
            .map(membershipMapper::toDTO)
            .toList();
    }

    /**
     * Get a specific membership by organization and user.
     *
     * @param organizationId The organization ID
     * @param userId         The user ID
     * @return The membership DTO
     * @throws ResourceNotFoundException if membership not found
     */
    public OrganizationMembershipDTO getMembershipByOrganizationAndUser(
            UUID organizationId, UUID userId) {
        logger.debug("Fetching membership for organization {} and user {}", 
                    organizationId, userId);

        OrganizationMembership membership = membershipRepository
            .findByOrganizationIdAndUserId(organizationId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Membership", userId.toString(),
                "Membership not found for user " + userId + " in organization " + organizationId));

        return membershipMapper.toDTO(membership);
    }

    /**
     * Get active memberships for an organization.
     *
     * @param organizationId The organization ID
     * @return List of active membership DTOs
     */
    public List<OrganizationMembershipDTO> getActiveMembershipsByOrganization(
            UUID organizationId) {
        logger.debug("Fetching active memberships for organization: {}", organizationId);

        List<OrganizationMembership> memberships = 
            membershipRepository.findByOrganizationIdAndStatus(
                organizationId, MembershipStatus.ACTIVE);

        return memberships.stream()
            .map(membershipMapper::toDTO)
            .toList();
    }

    /**
     * Update an existing membership.
     *
     * @param membershipId The membership ID
     * @param request      The update request
     * @return The updated membership DTO
     * @throws ResourceNotFoundException if membership not found
     */
    @Transactional
    public OrganizationMembershipDTO updateMembership(
            UUID membershipId, UpdateMembershipRequest request) {
        logger.info("Updating membership with id: {}", membershipId);

        OrganizationMembership membership = membershipRepository.findById(membershipId)
            .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId.toString()));

        // Update fields
        membership.setRole(request.role());
        membership.setStatus(request.status());

        OrganizationMembership updatedMembership = membershipRepository.save(membership);
        logger.info("Successfully updated membership with id: {}", membershipId);

        return membershipMapper.toDTO(updatedMembership);
    }

    /**
     * Delete a membership.
     *
     * @param membershipId The membership ID
     * @throws ResourceNotFoundException if membership not found
     */
    @Transactional
    public void deleteMembership(UUID membershipId) {
        logger.info("Deleting membership with id: {}", membershipId);

        if (!membershipRepository.existsById(membershipId)) {
            throw new ResourceNotFoundException("Membership", membershipId.toString());
        }

        membershipRepository.deleteById(membershipId);
        logger.info("Successfully deleted membership with id: {}", membershipId);
    }

    /**
     * Count active memberships in an organization.
     *
     * @param organizationId The organization ID
     * @return Count of active memberships
     */
    public long countActiveMemberships(UUID organizationId) {
        logger.debug("Counting active memberships for organization: {}", organizationId);
        return membershipRepository.countByOrganizationIdAndStatus(
            organizationId, MembershipStatus.ACTIVE);
    }
}
