package com.meicrypt.identity.organization.repository;

import com.meicrypt.identity.organization.entity.MembershipStatus;
import com.meicrypt.identity.organization.entity.OrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for OrganizationMembership entity.
 * Provides database access methods for membership management.
 */
@Repository
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {
    
    /**
     * Find all memberships for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of memberships
     */
    List<OrganizationMembership> findByOrganizationId(UUID organizationId);
    
    /**
     * Find all memberships for a specific user.
     *
     * @param userId The user ID
     * @return List of memberships
     */
    List<OrganizationMembership> findByUserId(UUID userId);
    
    /**
     * Find a specific membership by organization and user.
     *
     * @param organizationId The organization ID
     * @param userId         The user ID
     * @return Optional containing the membership if found
     */
    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
    
    /**
     * Find all active memberships for an organization.
     *
     * @param organizationId The organization ID
     * @param status         The membership status
     * @return List of active memberships
     */
    List<OrganizationMembership> findByOrganizationIdAndStatus(UUID organizationId, MembershipStatus status);
    
    /**
     * Check if a membership exists for organization and user.
     *
     * @param organizationId The organization ID
     * @param userId         The user ID
     * @return true if membership exists
     */
    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);
    
    /**
     * Count active memberships in an organization.
     *
     * @param organizationId The organization ID
     * @param status         The membership status
     * @return Count of memberships
     */
    long countByOrganizationIdAndStatus(UUID organizationId, MembershipStatus status);
}
