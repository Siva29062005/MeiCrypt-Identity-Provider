package com.meicrypt.identity.audit.dto;

import com.meicrypt.identity.audit.entity.AuditActorType;
import com.meicrypt.identity.audit.entity.AuditEvent;
import com.meicrypt.identity.audit.entity.AuditStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only projection of an audit trail row (Phase 11).
 */
public record AuditEventDTO(
        UUID id,
        UUID organizationId,
        UUID actorUserId,
        String actorEmail,
        AuditActorType actorType,
        String action,
        String resourceType,
        String resourceId,
        AuditStatus status,
        String ipAddress,
        String userAgent,
        String requestId,
        Map<String, Object> metadata,
        Instant occurredAt) {

    public static AuditEventDTO from(AuditEvent e) {
        return new AuditEventDTO(
                e.getId(),
                e.getOrganizationId(),
                e.getActorUserId(),
                e.getActorEmail(),
                e.getActorType(),
                e.getAction(),
                e.getResourceType(),
                e.getResourceId(),
                e.getStatus(),
                e.getIpAddress(),
                e.getUserAgent(),
                e.getRequestId(),
                e.getMetadata(),
                e.getOccurredAt());
    }
}
