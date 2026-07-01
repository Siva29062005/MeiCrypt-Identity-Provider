package com.meicrypt.identity.notification.repository;

import com.meicrypt.identity.notification.entity.Notification;
import com.meicrypt.identity.notification.entity.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox repository (Phase 10). The scheduler polls
 * {@link #findDispatchable(NotificationStatus, Instant, Pageable)} for rows
 * that are PENDING and past their {@code scheduled_at}.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("SELECT n FROM Notification n " +
           "WHERE n.status = :status AND n.scheduledAt <= :now " +
           "ORDER BY n.scheduledAt ASC")
    List<Notification> findDispatchable(@Param("status") NotificationStatus status,
                                        @Param("now") Instant now,
                                        Pageable pageable);

    List<Notification> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    long countByStatus(NotificationStatus status);
}
