package com.meicrypt.identity.audit.service;

import com.meicrypt.identity.audit.dto.AuditEventDTO;
import com.meicrypt.identity.audit.entity.AuditActorType;
import com.meicrypt.identity.audit.entity.AuditEvent;
import com.meicrypt.identity.audit.entity.AuditStatus;
import com.meicrypt.identity.audit.repository.AuditEventRepository;
import com.meicrypt.identity.auth.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Central write-through for the immutable audit trail (Phase 11 - Module 11.1).
 *
 * <p>Every domain service that mutates security-sensitive state MUST route
 * through {@link #record(String, AuditStatus, UUID, String, Map)} or one of
 * its overloads. Audit writes happen in a new transaction
 * ({@link Propagation#REQUIRES_NEW}) so that rolling back the caller's
 * business transaction never destroys the trail entry itself.
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    // ------------------------------------------------------------------
    // WRITE PATH
    // ------------------------------------------------------------------

    /**
     * Record a security-sensitive event using the current SecurityContext /
     * HttpServletRequest to enrich the row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent record(String action,
                             AuditStatus status,
                             UUID resourceIdOverride,
                             String resourceType,
                             Map<String, Object> metadata) {

        AuthenticatedUser principal = currentPrincipal();
        HttpServletRequest request = currentRequest();

        AuditEvent event = AuditEvent.builder()
                .organizationId(principal != null ? principal.organizationId() : null)
                .actorUserId(principal != null ? principal.userId() : null)
                .actorEmail(principal != null ? principal.email() : null)
                .actorType(principal != null ? AuditActorType.USER : AuditActorType.ANONYMOUS)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceIdOverride != null ? resourceIdOverride.toString() : null)
                .status(status)
                .ipAddress(clientIp(request))
                .userAgent(userAgent(request))
                .requestId(MDC.get("requestId"))
                .metadata(metadata)
                .occurredAt(Instant.now())
                .build();
        AuditEvent saved = auditEventRepository.save(event);
        logger.debug("Recorded audit event action={} status={} actor={} resource={}#{}",
                action, status, saved.getActorEmail(), resourceType, resourceIdOverride);
        return saved;
    }

    /**
     * Explicit variant used for pre-auth flows (login attempts, refresh
     * failures, ...) where we need to name the actor manually.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent recordForActor(String action,
                                     AuditStatus status,
                                     UUID organizationId,
                                     UUID actorUserId,
                                     String actorEmail,
                                     AuditActorType actorType,
                                     String resourceType,
                                     String resourceId,
                                     Map<String, Object> metadata) {
        HttpServletRequest request = currentRequest();
        AuditEvent event = AuditEvent.builder()
                .organizationId(organizationId)
                .actorUserId(actorUserId)
                .actorEmail(actorEmail)
                .actorType(actorType != null ? actorType : AuditActorType.ANONYMOUS)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .status(status)
                .ipAddress(clientIp(request))
                .userAgent(userAgent(request))
                .requestId(MDC.get("requestId"))
                .metadata(metadata)
                .occurredAt(Instant.now())
                .build();
        return auditEventRepository.save(event);
    }

    // ------------------------------------------------------------------
    // READ PATH
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AuditEventDTO> listForOrganization(UUID organizationId, int page, int size) {
        int p = Math.max(0, page);
        int s = (size <= 0 || size > 200) ? 50 : size;
        return auditEventRepository
                .findByOrganizationIdOrderByOccurredAtDesc(organizationId, PageRequest.of(p, s))
                .map(AuditEventDTO::from);
    }

    @Transactional(readOnly = true)
    public Page<AuditEventDTO> search(UUID organizationId, String action,
                                      Instant from, Instant to,
                                      int page, int size) {
        int p = Math.max(0, page);
        int s = (size <= 0 || size > 200) ? 50 : size;
        return auditEventRepository
                .search(organizationId, action, from, to, PageRequest.of(p, s))
                .map(AuditEventDTO::from);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private AuthenticatedUser currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser u)) {
            return null;
        }
        return u;
    }

    private HttpServletRequest currentRequest() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return sra.getRequest();
            }
        } catch (IllegalStateException ignored) {
            // background thread - no request bound
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > 500) {
            return ua.substring(0, 500);
        }
        return ua;
    }
}
