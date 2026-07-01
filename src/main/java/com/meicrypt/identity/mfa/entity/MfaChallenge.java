package com.meicrypt.identity.mfa.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Login-time step-up state (Phase 9). Issued after password verification when
 * the user has at least one ACTIVE {@link UserMfaFactor}; redeemed by the
 * client through the MFA verify endpoint to obtain a real token pair.
 */
@Entity
@Table(name = "mfa_challenges",
        indexes = {
                @Index(name = "idx_mfa_challenges_user", columnList = "user_id"),
                @Index(name = "idx_mfa_challenges_token", columnList = "challenge_token"),
                @Index(name = "idx_mfa_challenges_expiry", columnList = "expires_at")
        })
public class MfaChallenge {

    public enum Status { PENDING, SATISFIED, EXPIRED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "challenge_token", nullable = false, unique = true, length = 128)
    private String challengeToken;

    /**
     * Comma-separated list of {@link MfaFactorType} names accepted for this challenge.
     */
    @Column(name = "allowed_factor_types", nullable = false, length = 100)
    private String allowedFactorTypes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "satisfied_factor_id")
    private UUID satisfiedFactorId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "satisfied_at")
    private Instant satisfiedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    protected MfaChallenge() {
    }

    public MfaChallenge(UUID userId, UUID organizationId, String challengeToken,
                        String allowedFactorTypes, Instant expiresAt,
                        String ipAddress, String userAgent,
                        String deviceFingerprint, String deviceName) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.challengeToken = challengeToken;
        this.allowedFactorTypes = allowedFactorTypes;
        this.expiresAt = expiresAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceFingerprint = deviceFingerprint;
        this.deviceName = deviceName;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getOrganizationId() { return organizationId; }
    public String getChallengeToken() { return challengeToken; }
    public String getAllowedFactorTypes() { return allowedFactorTypes; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public UUID getSatisfiedFactorId() { return satisfiedFactorId; }
    public void setSatisfiedFactorId(UUID satisfiedFactorId) { this.satisfiedFactorId = satisfiedFactorId; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public String getDeviceName() { return deviceName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getSatisfiedAt() { return satisfiedAt; }
    public void setSatisfiedAt(Instant satisfiedAt) { this.satisfiedAt = satisfiedAt; }
    public int getAttempts() { return attempts; }
    public void incrementAttempts() { this.attempts++; }

    public boolean isPending() { return status == Status.PENDING; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
}
