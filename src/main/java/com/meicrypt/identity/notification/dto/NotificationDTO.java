package com.meicrypt.identity.notification.dto;

import com.meicrypt.identity.notification.entity.Notification;
import com.meicrypt.identity.notification.entity.NotificationChannel;
import com.meicrypt.identity.notification.entity.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of a notification outbox row (Phase 10).
 */
public record NotificationDTO(
        UUID id,
        UUID organizationId,
        UUID userId,
        NotificationChannel channel,
        String templateKey,
        String recipient,
        String subject,
        NotificationStatus status,
        int attemptCount,
        String lastError,
        Instant scheduledAt,
        Instant sentAt,
        Instant createdAt) {

    public static NotificationDTO from(Notification n) {
        return new NotificationDTO(
                n.getId(),
                n.getOrganizationId(),
                n.getUserId(),
                n.getChannel(),
                n.getTemplateKey(),
                n.getRecipient(),
                n.getSubject(),
                n.getStatus(),
                n.getAttemptCount(),
                n.getLastError(),
                n.getScheduledAt(),
                n.getSentAt(),
                n.getCreatedAt());
    }
}
