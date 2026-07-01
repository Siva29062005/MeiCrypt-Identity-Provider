package com.meicrypt.identity.organization.repository;

import com.meicrypt.identity.organization.entity.Organization;
import com.meicrypt.identity.organization.entity.OrganizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Organization entity.
 * Provides database access methods following domain boundary isolation.
 * 
 * Only the organization domain should directly access this repository.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    /**
     * Find an organization by its unique slug.
     *
     * @param slug The organization slug (URL-friendly identifier)
     * @return Optional containing the organization if found
     */
    Optional<Organization> findBySlug(String slug);

    /**
     * Check if an organization with the given slug exists.
     *
     * @param slug The organization slug to check
     * @return true if an organization with this slug exists
     */
    boolean existsBySlug(String slug);

    /**
     * Find all organizations with a specific status.
     *
     * @param status The organization status to filter by
     * @return List of organizations with the specified status
     */
    List<Organization> findByStatus(OrganizationStatus status);

    /**
     * Find organizations by name containing the search term (case-insensitive).
     *
     * @param name The search term
     * @return List of matching organizations
     */
    List<Organization> findByNameContainingIgnoreCase(String name);

    /**
     * Count organizations in a specific status. Used by the Phase 12
     * platform-admin dashboard.
     */
    long countByStatus(OrganizationStatus status);
}
