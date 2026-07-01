package com.meicrypt.identity.notification.exception;

/**
 * Raised when a caller references a template that is not present in the
 * catalog for the requested (channel, locale) combination (Phase 10).
 */
public class NotificationTemplateNotFoundException extends RuntimeException {

    public NotificationTemplateNotFoundException(String templateKey, String channel, String locale) {
        super(String.format("Notification template not found: key=%s channel=%s locale=%s",
                templateKey, channel, locale));
    }
}
