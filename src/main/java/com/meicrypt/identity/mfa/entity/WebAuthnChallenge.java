package com.meicrypt.identity.mfa.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One-shot server-issued WebAuthn challenge (Module 9.2). Consumed exactly
 * once by matching {@code challengeB64} + {@code challengeType} inside the
 * assertion / registration verifier.
 */
@Entity
@Table(name = "webauthn_challenges",
        indexes = {
                @Index(name = "idx_webauthn_challenges_user", columnList = "user_id"),
                @Index(name = "idx_webauthn_challenges_challenge", columnList = "challenge_b64"),
                @Index(name = "idx_webauthn_challenges_expiry", columnList = "expires_at")
        })
public class WebAuthnChallenge {

    public enum ChallengeType { REGISTRATION, ASSERTION }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "challenge_b64", nullable = false, length = 200)
    private String challengeBase64;

    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_type", nullable = false, length = 20)
    private ChallengeType challengeType;

    @Column(name = "relying_party_id", nullable = false, length = 255)
    private String relyingPartyId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected WebAuthnChallenge() {
    }

    public WebAuthnChallenge(UUID userId, String challengeBase64, ChallengeType challengeType,
                             String relyingPartyId, Instant expiresAt) {
        this.userId = userId;
        this.challengeBase64 = challengeBase64;
        this.challengeType = challengeType;
        this.relyingPartyId = relyingPartyId;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getChallengeBase64() { return challengeBase64; }
    public ChallengeType getChallengeType() { return challengeType; }
    public String getRelyingPartyId() { return relyingPartyId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isConsumed() { return consumedAt != null; }
}
