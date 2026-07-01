package com.meicrypt.identity.oauth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * OAuth2 authorization code (Phase 6, Module 6.1).
 *
 * <p>Codes are single-use, short-lived (default 60 s) and MUST be paired with
 * a PKCE {@code code_verifier} at the token endpoint. The plaintext code is
 * returned to the client via the redirect and never stored - only its
 * SHA-256 hash is persisted here.
 */
@Entity
@Table(name = "oauth_authorization_codes",
        indexes = {
                @Index(name = "idx_oauth_codes_client", columnList = "client_application_id"),
                @Index(name = "idx_oauth_codes_user", columnList = "user_id"),
                @Index(name = "idx_oauth_codes_expiry", columnList = "expires_at")
        })
public class OAuthAuthorizationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code_hash", nullable = false, unique = true, length = 128)
    private String codeHash;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "client_application_id", nullable = false)
    private UUID clientApplicationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "redirect_uri", nullable = false, length = 1000)
    private String redirectUri;

    @Column(name = "scopes", nullable = false, length = 1000)
    private String scopes;

    @Column(name = "code_challenge", nullable = false, length = 255)
    private String codeChallenge;

    @Column(name = "code_challenge_method", nullable = false, length = 10)
    private String codeChallengeMethod = "S256";

    @Column(name = "state", length = 500)
    private String state;

    @Column(name = "nonce", length = 500)
    private String nonce;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected OAuthAuthorizationCode() {
    }

    public OAuthAuthorizationCode(String codeHash, UUID organizationId, UUID clientApplicationId,
                                  UUID userId, UUID sessionId, String redirectUri, String scopes,
                                  String codeChallenge, String codeChallengeMethod,
                                  String state, String nonce, Instant expiresAt) {
        this.codeHash = codeHash;
        this.organizationId = organizationId;
        this.clientApplicationId = clientApplicationId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.codeChallenge = codeChallenge;
        this.codeChallengeMethod = codeChallengeMethod;
        this.state = state;
        this.nonce = nonce;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getCodeHash() { return codeHash; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getClientApplicationId() { return clientApplicationId; }
    public UUID getUserId() { return userId; }
    public UUID getSessionId() { return sessionId; }
    public String getRedirectUri() { return redirectUri; }
    public String getScopes() { return scopes; }
    public String getCodeChallenge() { return codeChallenge; }
    public String getCodeChallengeMethod() { return codeChallengeMethod; }
    public String getState() { return state; }
    public String getNonce() { return nonce; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }

    public boolean isConsumed() { return consumedAt != null; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isUsable() { return !isConsumed() && !isExpired(); }
}
