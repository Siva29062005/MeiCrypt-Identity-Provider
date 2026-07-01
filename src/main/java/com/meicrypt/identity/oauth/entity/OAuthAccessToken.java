package com.meicrypt.identity.oauth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Registry entry for an OAuth2 access token (Phase 6, Module 6.2).
 *
 * The JWT itself is returned to the client only once at issuance. The server
 * keeps the SHA-256 hash + jti so it can:
 *   - answer RFC 7662 introspection queries,
 *   - revoke instantly on logout / secret rotation,
 *   - enforce scopes independently of the JWT signature check.
 */
@Entity
@Table(name = "oauth_access_tokens",
        indexes = {
                @Index(name = "idx_oauth_access_client", columnList = "client_application_id"),
                @Index(name = "idx_oauth_access_user", columnList = "user_id"),
                @Index(name = "idx_oauth_access_expiry", columnList = "expires_at")
        })
public class OAuthAccessToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "jwt_id", nullable = false, unique = true, length = 64)
    private String jwtId;

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

    @Column(name = "audience", length = 500)
    private String audience;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 200)
    private String revokedReason;

    protected OAuthAccessToken() {
    }

    public OAuthAccessToken(String tokenHash, String jwtId, UUID organizationId,
                            UUID clientApplicationId, UUID userId, UUID sessionId,
                            String scopes, String audience, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.jwtId = jwtId;
        this.organizationId = organizationId;
        this.clientApplicationId = clientApplicationId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.scopes = scopes;
        this.audience = audience;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public String getJwtId() { return jwtId; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getClientApplicationId() { return clientApplicationId; }
    public UUID getUserId() { return userId; }
    public UUID getSessionId() { return sessionId; }
    public String getScopes() { return scopes; }
    public String getAudience() { return audience; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokedReason() { return revokedReason; }

    public void revoke(String reason) {
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
    }

    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isActive() { return !isRevoked() && !isExpired(); }
}
