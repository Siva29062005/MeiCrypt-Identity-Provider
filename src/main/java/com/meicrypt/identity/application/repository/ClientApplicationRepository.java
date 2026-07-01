package com.meicrypt.identity.application.repository;

import com.meicrypt.identity.application.entity.ApplicationStatus;
import com.meicrypt.identity.application.entity.ClientApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA access for {@link ClientApplication}.
 *
 * All lookup methods are organization-scoped to preserve tenant isolation;
 * the single {@link #findByClientId(String)} lookup is required by the OAuth
 * engine (Phase 6) which authenticates strictly by the opaque {@code clientId}.
 */
@Repository
public interface ClientApplicationRepository extends JpaRepository<ClientApplication, UUID> {

    List<ClientApplication> findByOrganizationId(UUID organizationId);

    List<ClientApplication> findByOrganizationIdAndStatus(UUID organizationId, ApplicationStatus status);

    Optional<ClientApplication> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<ClientApplication> findByOrganizationIdAndSlug(UUID organizationId, String slug);

    Optional<ClientApplication> findByClientId(String clientId);

    boolean existsByOrganizationIdAndSlug(UUID organizationId, String slug);

    boolean existsByClientId(String clientId);
}
