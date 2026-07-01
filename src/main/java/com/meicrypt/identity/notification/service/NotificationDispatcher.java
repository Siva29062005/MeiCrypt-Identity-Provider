package com.meicrypt.identity.notification.service;

import com.meicrypt.identity.notification.config.NotificationProperties;
import com.meicrypt.identity.notification.entity.Notification;
import com.meicrypt.identity.notification.entity.NotificationStatus;
import com.meicrypt.identity.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Background worker draining the notification outbox (Phase 10).
 *
 * <p>Every second the scheduler pulls at most
 * {@link NotificationProperties#getBatchSize()} rows that are PENDING and
 * whose {@code scheduledAt} is due. Each row is dispatched independently:
 * a failure only affects that row's retry counter.
 *
 * <p>Design invariant: dispatch has at-least-once semantics. Idempotency at
 * the destination side (e.g. deduplication keys in email providers) is the
 * responsibility of the transport implementation.
 */
@Component
public class NotificationDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository notificationRepository;
    private final NotificationProperties properties;
    private final List<NotificationTransport> transports;

    public NotificationDispatcher(NotificationRepository notificationRepository,
                                  NotificationProperties properties,
                                  List<NotificationTransport> transports) {
        this.notificationRepository = notificationRepository;
        this.properties = properties;
        this.transports = transports;
    }

    @Scheduled(fixedDelayString = "${meicrypt.notifications.poll-interval-ms:1000}")
    public void poll() {
        if (!properties.isWorkerEnabled()) {
            return;
        }
        List<Notification> batch = notificationRepository.findDispatchable(
                NotificationStatus.PENDING, Instant.now(),
                PageRequest.of(0, properties.getBatchSize()));
        for (Notification n : batch) {
            dispatchSingle(n.getId());
        }
    }

    @Transactional
    public void dispatchSingle(java.util.UUID id) {
        Notification n = notificationRepository.findById(id).orElse(null);
        if (n == null || n.getStatus() != NotificationStatus.PENDING) {
            return;
        }
        n.setStatus(NotificationStatus.SENDING);
        n.incrementAttempt();
        notificationRepository.save(n);

        Optional<NotificationTransport> transport = transports.stream()
                .filter(t -> t.supports(n))
                .findFirst();
        if (transport.isEmpty()) {
            n.setLastError("No transport supports this notification");
            n.setStatus(NotificationStatus.FAILED);
            notificationRepository.save(n);
            logger.error("Notification {} has no eligible transport", n.getId());
            return;
        }

        try {
            transport.get().deliver(n);
            n.setStatus(NotificationStatus.SENT);
            n.setSentAt(Instant.now());
            n.setLastError(null);
            notificationRepository.save(n);
        } catch (RuntimeException ex) {
            n.setLastError(truncate(ex.getMessage(), 1000));
            if (n.getAttemptCount() >= properties.getMaxAttempts()) {
                n.setStatus(NotificationStatus.FAILED);
                logger.error("Notification {} permanently FAILED after {} attempts: {}",
                        n.getId(), n.getAttemptCount(), ex.getMessage());
            } else {
                n.setStatus(NotificationStatus.PENDING);
                // Exponential-ish backoff: schedule for a few seconds ahead
                n.setScheduledAt(Instant.now().plusSeconds(5L * n.getAttemptCount()));
                logger.warn("Notification {} attempt {} failed, will retry: {}",
                        n.getId(), n.getAttemptCount(), ex.getMessage());
            }
            notificationRepository.save(n);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
