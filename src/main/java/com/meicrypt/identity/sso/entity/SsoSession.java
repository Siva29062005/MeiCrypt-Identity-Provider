package com.meicrypt.identity.sso.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * SSO session record (Phase 8, Module 8.1).
 *
 * <p>Anchors a Phase-3 {@code user_sessions} row so multiple OAuth client
 * applications can be authorized from the same browser without re-prompting
 * for credentials. On termination all participants recorded in
 * {@link SsoSessionParticipant} are notified via Back-Channel Logout
 * (Module 8.2).
 */
@Entity
@Table(name = "sso_sessions", indexes = {
        @Index(name = "idx_sso_sessions_user", columnList = "user_id"),
        @Index(name = "idx_sso_sessions_org", columnList = "organization_id"),
        @Index(name = "idx_sso_sessions_status", columnList = "status"),
        @Index(name = "idx_sso_sessions_expiry", columnList = "expires_at")
})
public class SsoSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_session_id", nullable = false, unique = true)
    private UUID userSessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "sso_id", nullable = false, unique = true, length = 64)
    private String ssoId;

    @CreationTimestamp
    @Column(name = "authenticated_at", nullable = false, updatable = false)
    private Instant authenticatedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SsoSessionStatus status = SsoSessionStatus.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "terminated_at")
    private Instant terminatedAt;

    @Column(name = "termination_reason", length = 200)
    private String terminationReason;

    protected SsoSession() {
    }

    public SsoSession(UUID userSessionId, UUID userId, UUID organizationId,
                      String ssoId, String ipAddress, String userAgent, Instant expiresAt) {
        this.userSessionId = userSessionId;
        this.userId = userId;
        this.organizationId = organizationId;
        this.ssoId = ssoId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.expiresAt = expiresAt;
        this.status = SsoSessionStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public UUID getUserSessionId() { return userSessionId; }
    public UUID getUserId() { return userId; }
    public UUID getOrganizationId() { return organizationId; }
    public String getSsoId() { return ssoId; }
    public Instant getAuthenticatedAt() { return authenticatedAt; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public SsoSessionStatus getStatus() { return status; }
    public void setStatus(SsoSessionStatus status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getTerminatedAt() { return terminatedAt; }
    public void setTerminatedAt(Instant terminatedAt) { this.terminatedAt = terminatedAt; }
    public String getTerminationReason() { return terminationReason; }
    public void setTerminationReason(String terminationReason) { this.terminationReason = terminationReason; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return status == SsoSessionStatus.ACTIVE && !isExpired();
    }
}
