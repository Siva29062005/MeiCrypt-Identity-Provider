package com.meicrypt.identity.notification.entity;

/**
 * Supported outbound notification channels (Phase 10 - Module 10.1).
 * Two initial transports are supported; adding a third (push, webhook, ...)
 * is a matter of implementing a new {@link com.meicrypt.identity.notification.service.NotificationTransport}.
 */
public enum NotificationChannel {
    EMAIL,
    SMS
}
