package com.meicrypt.identity.organization.repository;

import com.meicrypt.identity.organization.entity.OrganizationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for OrganizationSettings entity.
 * Provides database access methods for organization settings.
 */
@Repository
public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, UUID> {

    /**
     * Find organization settings by organization ID.
     *
     * @param organizationId The organization ID
     * @return Optional containing the settings if found
     */
    Optional<OrganizationSettings> findByOrganizationId(UUID organizationId);

    /**
     * Check if settings exist for the given organization ID.
     *
     * @param organizationId The organization ID
     * @return true if settings exist for this organization
     */
    boolean existsByOrganizationId(UUID organizationId);

    /**
     * Delete settings by organization ID.
     *
     * @param organizationId The organization ID
     */
    void deleteByOrganizationId(UUID organizationId);
}
