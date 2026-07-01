package com.meicrypt.identity.notification.service;

import com.meicrypt.identity.notification.config.NotificationProperties;
import com.meicrypt.identity.notification.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default transport used in local / test environments (Phase 10).
 *
 * <p>Writes a structured line to the application log instead of contacting
 * an external SMTP or SMS gateway. Production deployments must replace this
 * bean with a real transport (e.g. AWS SES, Twilio) via a
 * {@code @ConditionalOnProperty} override.
 */
@Component
public class LoggingNotificationTransport implements NotificationTransport {

    private static final Logger logger = LoggerFactory.getLogger(LoggingNotificationTransport.class);
    private final NotificationProperties properties;

    public LoggingNotificationTransport(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(Notification notification) {
        // Always supports; acts as the fallback transport when enabled.
        return properties.isLogOnlyTransport();
    }

    @Override
    public void deliver(Notification notification) {
        logger.info("[NOTIFICATION-DISPATCH] id={} channel={} to={} subject={} template={}",
                notification.getId(),
                notification.getChannel(),
                notification.getRecipient(),
                notification.getSubject(),
                notification.getTemplateKey());
        logger.debug("[NOTIFICATION-BODY] id={} body={}",
                notification.getId(), notification.getBody());
    }
}
