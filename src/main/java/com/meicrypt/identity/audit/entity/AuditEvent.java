package com.meicrypt.identity.audit.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only structured audit event (Phase 11 - Module 11.1).
 *
 * <p><strong>Immutability contract:</strong> once persisted, an {@code AuditEvent}
 * must never be updated or deleted. The application must expose no service or
 * repository method that mutates this row. The database schema does not
 * enforce this (a database trigger doing so is left to operator-level
 * configuration) so the enforcement lives at the code boundary here — the
 * entity has no setters.
 */
@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_events_org_occurred",
                columnList = "organization_id, occurred_at DESC"),
        @Index(name = "idx_audit_events_actor_occurred",
                columnList = "actor_user_id, occurred_at DESC"),
        @Index(name = "idx_audit_events_action",       columnList = "action"),
        @Index(name = "idx_audit_events_occurred_at",  columnList = "occurred_at DESC")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_email", length = 320)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 30)
    private AuditActorType actorType = AuditActorType.USER;

    @Column(name = "action", nullable = false, length = 150)
    private String action;

    @Column(name = "resource_type", length = 80)
    private String resourceType;

    @Column(name = "resource_id", length = 150)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuditStatus status;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_id", length = 80)
    private String requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    protected AuditEvent() {
    }

    private AuditEvent(Builder b) {
        this.organizationId = b.organizationId;
        this.actorUserId    = b.actorUserId;
        this.actorEmail     = b.actorEmail;
        this.actorType      = b.actorType != null ? b.actorType : AuditActorType.USER;
        this.action         = b.action;
        this.resourceType   = b.resourceType;
        this.resourceId     = b.resourceId;
        this.status         = b.status != null ? b.status : AuditStatus.SUCCESS;
        this.ipAddress      = b.ipAddress;
        this.userAgent      = b.userAgent;
        this.requestId      = b.requestId;
        this.metadata       = b.metadata;
        this.occurredAt     = b.occurredAt != null ? b.occurredAt : Instant.now();
    }

    // ---- read-only accessors (immutable contract) -------------------------
    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorEmail() { return actorEmail; }
    public AuditActorType getActorType() { return actorType; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public AuditStatus getStatus() { return status; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getRequestId() { return requestId; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getOccurredAt() { return occurredAt; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID organizationId;
        private UUID actorUserId;
        private String actorEmail;
        private AuditActorType actorType;
        private String action;
        private String resourceType;
        private String resourceId;
        private AuditStatus status;
        private String ipAddress;
        private String userAgent;
        private String requestId;
        private Map<String, Object> metadata;
        private Instant occurredAt;

        public Builder organizationId(UUID v) { this.organizationId = v; return this; }
        public Builder actorUserId(UUID v)    { this.actorUserId    = v; return this; }
        public Builder actorEmail(String v)   { this.actorEmail     = v; return this; }
        public Builder actorType(AuditActorType v) { this.actorType = v; return this; }
        public Builder action(String v)       { this.action         = v; return this; }
        public Builder resourceType(String v) { this.resourceType   = v; return this; }
        public Builder resourceId(String v)   { this.resourceId     = v; return this; }
        public Builder status(AuditStatus v)  { this.status         = v; return this; }
        public Builder ipAddress(String v)    { this.ipAddress      = v; return this; }
        public Builder userAgent(String v)    { this.userAgent      = v; return this; }
        public Builder requestId(String v)    { this.requestId      = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }
        public Builder occurredAt(Instant v)  { this.occurredAt     = v; return this; }

        public AuditEvent build() { return new AuditEvent(this); }
    }
}
