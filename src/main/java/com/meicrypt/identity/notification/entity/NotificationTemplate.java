package com.meicrypt.identity.notification.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Named, versioned notification content template (Phase 10 - Module 10.1).
 *
 * <p>Templates are keyed by {@code (templateKey, channel, locale)} and
 * managed exclusively through Flyway migrations - no runtime mutation
 * endpoints. Bodies are Handlebars-style ({@code {{firstName}}}) and
 * rendered by {@code NotificationRenderer}.
 */
@Entity
@Table(name = "notification_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "unique_template_key_channel_locale",
                        columnNames = {"template_key", "channel", "locale"})
        },
        indexes = {
                @Index(name = "idx_notification_templates_key",     columnList = "template_key"),
                @Index(name = "idx_notification_templates_channel", columnList = "channel")
        })
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "template_key", nullable = false, length = 150)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale = "en";

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationTemplate() {
    }

    public NotificationTemplate(String templateKey, NotificationChannel channel,
                                String locale, String subject, String bodyTemplate) {
        this.templateKey = templateKey;
        this.channel = channel;
        this.locale = locale;
        this.subject = subject;
        this.bodyTemplate = bodyTemplate;
    }

    public UUID getId() { return id; }
    public String getTemplateKey() { return templateKey; }
    public NotificationChannel getChannel() { return channel; }
    public String getLocale() { return locale; }
    public String getSubject() { return subject; }
    public String getBodyTemplate() { return bodyTemplate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
