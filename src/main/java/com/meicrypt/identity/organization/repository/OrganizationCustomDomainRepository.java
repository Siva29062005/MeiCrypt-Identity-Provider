package com.meicrypt.identity.organization.repository;

import com.meicrypt.identity.organization.entity.DomainVerificationStatus;
import com.meicrypt.identity.organization.entity.OrganizationCustomDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for OrganizationCustomDomain entity.
 * Provides database access methods for custom domain management.
 */
@Repository
public interface OrganizationCustomDomainRepository extends JpaRepository<OrganizationCustomDomain, UUID> {
    
    /**
     * Find a custom domain by domain name.
     *
     * @param domain The domain name
     * @return Optional containing the domain if found
     */
    Optional<OrganizationCustomDomain> findByDomain(String domain);
    
    /**
     * Find all custom domains for a specific organization.
     *
     * @param organizationId The organization ID
     * @return List of custom domains
     */
    List<OrganizationCustomDomain> findByOrganizationId(UUID organizationId);
    
    /**
     * Find custom domains by organization and verification status.
     *
     * @param organizationId     The organization ID
     * @param verificationStatus The verification status
     * @return List of custom domains
     */
    List<OrganizationCustomDomain> findByOrganizationIdAndVerificationStatus(
        UUID organizationId, DomainVerificationStatus verificationStatus);
    
    /**
     * Find the primary domain for an organization.
     *
     * @param organizationId The organization ID
     * @param isPrimary      The primary flag
     * @return Optional containing the primary domain if found
     */
    Optional<OrganizationCustomDomain> findByOrganizationIdAndIsPrimary(
        UUID organizationId, Boolean isPrimary);
    
    /**
     * Check if a domain already exists.
     *
     * @param domain The domain name
     * @return true if domain exists
     */
    boolean existsByDomain(String domain);
    
    /**
     * Find a custom domain by verification token.
     *
     * @param verificationToken The verification token
     * @return Optional containing the domain if found
     */
    Optional<OrganizationCustomDomain> findByVerificationToken(String verificationToken);
}
