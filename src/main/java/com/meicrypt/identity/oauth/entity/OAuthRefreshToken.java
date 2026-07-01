package com.meicrypt.identity.oauth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * OAuth2 refresh token (Phase 6, Module 6.1) - separate from the Phase 3
 * user-session refresh token. Rotated on every use; reuse of a rotated token
 * marks the whole chain COMPROMISED and revokes the paired access token.
 */
@Entity
@Table(name = "oauth_refresh_tokens",
        indexes = {
                @Index(name = "idx_oauth_refresh_client", columnList = "client_application_id"),
                @Index(name = "idx_oauth_refresh_user", columnList = "user_id"),
                @Index(name = "idx_oauth_refresh_status", columnList = "status")
        })
public class OAuthRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "client_application_id", nullable = false)
    private UUID clientApplicationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "scopes", nullable = false, length = 1000)
    private String scopes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OAuthRefreshTokenStatus status = OAuthRefreshTokenStatus.ACTIVE;

    @Column(name = "parent_token_hash", length = 128)
    private String parentTokenHash;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 200)
    private String revokedReason;

    protected OAuthRefreshToken() {
    }

    public OAuthRefreshToken(String tokenHash, UUID organizationId, UUID clientApplicationId,
                             UUID userId, UUID sessionId, String scopes, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.organizationId = organizationId;
        this.clientApplicationId = clientApplicationId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.scopes = scopes;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getClientApplicationId() { return clientApplicationId; }
    public UUID getUserId() { return userId; }
    public UUID getSessionId() { return sessionId; }
    public String getScopes() { return scopes; }
    public OAuthRefreshTokenStatus getStatus() { return status; }
    public void setStatus(OAuthRefreshTokenStatus status) { this.status = status; }
    public String getParentTokenHash() { return parentTokenHash; }
    public void setParentTokenHash(String parentTokenHash) { this.parentTokenHash = parentTokenHash; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedReason() { return revokedReason; }
    public void setRevokedReason(String revokedReason) { this.revokedReason = revokedReason; }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isActive() {
        return status == OAuthRefreshTokenStatus.ACTIVE && !isExpired();
    }
}
