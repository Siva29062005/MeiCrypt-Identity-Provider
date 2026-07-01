package com.meicrypt.identity.notification.dto;

import com.meicrypt.identity.notification.entity.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * Enqueue request accepted by {@code POST /api/v1/notifications} (Phase 10).
 *
 * <p>Either {@code templateKey} + {@code parameters} is provided (rendered
 * through {@link com.meicrypt.identity.notification.entity.NotificationTemplate})
 * OR {@code body} + optional {@code subject} directly. Validation enforced in
 * service layer.
 */
public record SendNotificationRequest(
        UUID organizationId,
        UUID userId,
        @NotNull NotificationChannel channel,
        @NotBlank @Size(max = 320) String recipient,
        @Size(max = 150) String templateKey,
        Map<String, String> parameters,
        @Size(max = 10)  String locale,
        @Size(max = 500) String subject,
        String body) {
}
