package com.meicrypt.identity.notification.repository;

import com.meicrypt.identity.notification.entity.NotificationChannel;
import com.meicrypt.identity.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for named notification templates (Phase 10).
 */
@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByTemplateKeyAndChannelAndLocale(
            String templateKey, NotificationChannel channel, String locale);

    Optional<NotificationTemplate> findFirstByTemplateKeyAndChannelOrderByLocaleAsc(
            String templateKey, NotificationChannel channel);
}
