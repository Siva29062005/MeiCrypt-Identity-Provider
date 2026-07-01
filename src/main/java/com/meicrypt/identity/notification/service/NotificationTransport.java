package com.meicrypt.identity.notification.service;

import com.meicrypt.identity.notification.entity.Notification;

/**
 * Pluggable transport layer for the notification worker (Phase 10).
 *
 * <p>Concrete implementations bind to a channel (SMTP, SES, Twilio, ...);
 * the default {@link LoggingNotificationTransport} simply logs the payload,
 * making local development safe without external accounts.
 */
public interface NotificationTransport {

    /**
     * @return {@code true} when this transport can carry the given notification.
     */
    boolean supports(Notification notification);

    /**
     * Deliver a rendered notification. Must throw on any downstream failure -
     * the worker interprets a thrown exception as a retryable dispatch error.
     */
    void deliver(Notification notification);
}
