package com.meicrypt.identity.sso.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Association row between an {@link SsoSession} and a
 * {@code client_application} that has obtained tokens through it.
 *
 * <p>Written by the OAuth authorization service every time a client goes
 * through the {@code /oauth2/authorize} flow while an SSO session is active.
 * On single-logout, every participant is enumerated to fan out Back-Channel
 * Logout notifications (Module 8.2).
 */
@Entity
@Table(name = "sso_session_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "unique_sso_participant",
                columnNames = {"sso_session_id", "client_application_id"}),
        indexes = {
                @Index(name = "idx_sso_participant_sso", columnList = "sso_session_id"),
                @Index(name = "idx_sso_participant_client", columnList = "client_application_id")
        })
public class SsoSessionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sso_session_id", nullable = false)
    private UUID ssoSessionId;

    @Column(name = "client_application_id", nullable = false)
    private UUID clientApplicationId;

    @CreationTimestamp
    @Column(name = "first_authorized_at", nullable = false, updatable = false)
    private Instant firstAuthorizedAt;

    @Column(name = "last_authorized_at", nullable = false)
    private Instant lastAuthorizedAt;

    @Column(name = "last_scope", length = 1000)
    private String lastScope;

    @Column(name = "logout_notified_at")
    private Instant logoutNotifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "logout_notification_state", length = 20)
    private LogoutNotificationState logoutNotificationState;

    @Column(name = "logout_notification_error", length = 500)
    private String logoutNotificationError;

    protected SsoSessionParticipant() {
    }

    public SsoSessionParticipant(UUID ssoSessionId, UUID clientApplicationId, String scope) {
        this.ssoSessionId = ssoSessionId;
        this.clientApplicationId = clientApplicationId;
        this.lastScope = scope;
        this.lastAuthorizedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSsoSessionId() { return ssoSessionId; }
    public UUID getClientApplicationId() { return clientApplicationId; }
    public Instant getFirstAuthorizedAt() { return firstAuthorizedAt; }
    public Instant getLastAuthorizedAt() { return lastAuthorizedAt; }
    public void setLastAuthorizedAt(Instant lastAuthorizedAt) { this.lastAuthorizedAt = lastAuthorizedAt; }
    public String getLastScope() { return lastScope; }
    public void setLastScope(String lastScope) { this.lastScope = lastScope; }
    public Instant getLogoutNotifiedAt() { return logoutNotifiedAt; }
    public void setLogoutNotifiedAt(Instant logoutNotifiedAt) { this.logoutNotifiedAt = logoutNotifiedAt; }
    public LogoutNotificationState getLogoutNotificationState() { return logoutNotificationState; }
    public void setLogoutNotificationState(LogoutNotificationState logoutNotificationState) {
        this.logoutNotificationState = logoutNotificationState;
    }
    public String getLogoutNotificationError() { return logoutNotificationError; }
    public void setLogoutNotificationError(String logoutNotificationError) {
        this.logoutNotificationError = logoutNotificationError;
    }

    public enum LogoutNotificationState {
        PENDING, SENT, FAILED, SKIPPED
    }
}
