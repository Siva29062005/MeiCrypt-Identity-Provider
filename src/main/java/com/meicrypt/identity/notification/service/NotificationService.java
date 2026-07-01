package com.meicrypt.identity.notification.service;

import com.meicrypt.identity.notification.config.NotificationProperties;
import com.meicrypt.identity.notification.dto.NotificationDTO;
import com.meicrypt.identity.notification.dto.SendNotificationRequest;
import com.meicrypt.identity.notification.entity.Notification;
import com.meicrypt.identity.notification.entity.NotificationChannel;
import com.meicrypt.identity.notification.entity.NotificationStatus;
import com.meicrypt.identity.notification.entity.NotificationTemplate;
import com.meicrypt.identity.notification.exception.NotificationTemplateNotFoundException;
import com.meicrypt.identity.notification.repository.NotificationRepository;
import com.meicrypt.identity.notification.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public entry point for enqueueing notifications (Phase 10 - Module 10.1).
 *
 * <p>The service always writes to the outbox synchronously and returns
 * immediately; actual dispatch happens asynchronously via
 * {@link NotificationDispatcher}. This separation guarantees that callers
 * never block on remote gateways and that transient transport failures do
 * not fail the originating business operation.
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationRenderer renderer;
    private final NotificationProperties properties;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationTemplateRepository templateRepository,
                               NotificationRenderer renderer,
                               NotificationProperties properties) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.renderer = renderer;
        this.properties = properties;
    }

    public NotificationDTO enqueue(SendNotificationRequest request) {
        String subject = request.subject();
        String body = request.body();

        if (request.templateKey() != null && !request.templateKey().isBlank()) {
            String locale = (request.locale() == null || request.locale().isBlank())
                    ? properties.getDefaultLocale() : request.locale();
            NotificationTemplate template = templateRepository
                    .findByTemplateKeyAndChannelAndLocale(request.templateKey(),
                            request.channel(), locale)
                    .or(() -> templateRepository.findFirstByTemplateKeyAndChannelOrderByLocaleAsc(
                            request.templateKey(), request.channel()))
                    .orElseThrow(() -> new NotificationTemplateNotFoundException(
                            request.templateKey(), request.channel().name(), locale));
            Map<String, String> params = request.parameters() == null
                    ? Map.of() : request.parameters();
            subject = renderer.render(template.getSubject(), params);
            body = renderer.render(template.getBodyTemplate(), params);
        }

        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException(
                    "Notification body cannot be empty (provide body or a templateKey).");
        }

        Notification n = new Notification(
                request.organizationId(),
                request.userId(),
                request.channel(),
                request.templateKey(),
                request.recipient(),
                subject,
                body);
        n = notificationRepository.save(n);
        logger.debug("Enqueued notification {} channel={} recipient={}",
                n.getId(), n.getChannel(), n.getRecipient());
        return NotificationDTO.from(n);
    }

    /**
     * Convenience helper used by internal callers (verification service,
     * invitation service, ...): render a template and enqueue in one call.
     */
    public NotificationDTO enqueueFromTemplate(UUID organizationId,
                                               UUID userId,
                                               NotificationChannel channel,
                                               String templateKey,
                                               String recipient,
                                               Map<String, String> params) {
        SendNotificationRequest req = new SendNotificationRequest(
                organizationId, userId, channel, recipient, templateKey,
                params, properties.getDefaultLocale(), null, null);
        return enqueue(req);
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> listRecent(UUID organizationId, int limit) {
        int size = (limit <= 0 || limit > 200) ? 50 : limit;
        return notificationRepository
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId, PageRequest.of(0, size))
                .stream().map(NotificationDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return notificationRepository.countByStatus(NotificationStatus.PENDING);
    }
}
