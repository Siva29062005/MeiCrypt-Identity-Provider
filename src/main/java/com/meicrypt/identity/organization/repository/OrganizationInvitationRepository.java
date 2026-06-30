package com.meicrypt.identity.organization.repository;

import com.meicrypt.identity.organization.entity.InvitationStatus;
import com.meicrypt.identity.organization.entity.OrganizationInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for OrganizationInvitation entity.
 * Provides database access methods for invitation management.
 */
@Repository
public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, UUID> {
    
    /**
     * Find an invitation by its token.
     *
     * @param invitationToken The invitation token
     * @return Optional containing the invitation if found
     */
    Optional<OrganizationInvitation> findByInvitationToken(String invitationToken);
    
    /**
     * Find all invitations for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of invitations
     */
    List<OrganizationInvitation> findByOrganizationId(UUID organizationId);
    
    /**
     * Find all invitations for a specific email.
     *
     * @param email The email address
     * @return List of invitations
     */
    List<OrganizationInvitation> findByEmail(String email);
    
    /**
     * Find invitations by organization and status.
     *
     * @param organizationId The organization ID
     * @param status         The invitation status
     * @return List of invitations
     */
    List<OrganizationInvitation> findByOrganizationIdAndStatus(UUID organizationId, InvitationStatus status);
    
    /**
     * Find pending invitations for a specific email in an organization.
     *
     * @param organizationId The organization ID
     * @param email         The email address
     * @param status        The invitation status
     * @return List of pending invitations
     */
    List<OrganizationInvitation> findByOrganizationIdAndEmailAndStatus(
        UUID organizationId, String email, InvitationStatus status);
    
    /**
     * Find all expired invitations that are still marked as pending.
     *
     * @param status The invitation status
     * @param now    The current timestamp
     * @return List of expired pending invitations
     */
    List<OrganizationInvitation> findByStatusAndExpiresAtBefore(InvitationStatus status, Instant now);
    
    /**
     * Check if a pending invitation exists for email in organization.
     *
     * @param organizationId The organization ID
     * @param email         The email address
     * @param status        The invitation status
     * @return true if pending invitation exists
     */
    boolean existsByOrganizationIdAndEmailAndStatus(UUID organizationId, String email, InvitationStatus status);
}
