package com.meicrypt.identity.audit.repository;

import com.meicrypt.identity.audit.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Read repository for the audit trail (Phase 11 - Module 11.1).
 *
 * <p>Only query methods are exposed. Writes go exclusively through
 * {@code AuditService.record(...)} which uses {@code EntityManager.persist}
 * on the injected persistence context.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByOrganizationIdOrderByOccurredAtDesc(UUID organizationId, Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE a.organizationId = :orgId " +
           "AND (:action IS NULL OR a.action = :action) " +
           "AND (:from IS NULL OR a.occurredAt >= :from) " +
           "AND (:to   IS NULL OR a.occurredAt <= :to) " +
           "ORDER BY a.occurredAt DESC")
    Page<AuditEvent> search(@Param("orgId") UUID orgId,
                            @Param("action") String action,
                            @Param("from") Instant from,
                            @Param("to")   Instant to,
                            Pageable pageable);
}
