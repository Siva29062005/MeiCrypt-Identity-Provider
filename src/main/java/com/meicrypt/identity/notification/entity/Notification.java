package com.meicrypt.identity.notification.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent outbox row for asynchronous notification dispatch
 * (Phase 10 - Module 10.1).
 *
 * <p>Provides at-least-once semantics: a scheduler picks up
 * {@link NotificationStatus#PENDING} rows, transitions them to
 * {@link NotificationStatus#SENDING}, then to {@link NotificationStatus#SENT}
 * or {@link NotificationStatus#FAILED} with an incremented {@code attemptCount}.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_status_scheduled",
                columnList = "status, scheduled_at"),
        @Index(name = "idx_notifications_organization_id", columnList = "organization_id"),
        @Index(name = "idx_notifications_user_id",         columnList = "user_id")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "template_key", length = 150)
    private String templateKey;

    @Column(name = "recipient", nullable = false, length = 320)
    private String recipient;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Notification() {
    }

    public Notification(UUID organizationId, UUID userId, NotificationChannel channel,
                        String templateKey, String recipient, String subject, String body) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.channel = channel;
        this.templateKey = templateKey;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    // --- getters / setters ---------------------------------------------

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getUserId() { return userId; }
    public NotificationChannel getChannel() { return channel; }
    public String getTemplateKey() { return templateKey; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public NotificationStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(NotificationStatus status) { this.status = status; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public void incrementAttempt() { this.attemptCount++; }
}
