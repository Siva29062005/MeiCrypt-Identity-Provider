package com.meicrypt.identity.mfa.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract second-factor bound to a user (Phase 9). Its concrete secret material
 * lives in a sibling table keyed by {@code factor_id}
 * ({@link TotpEnrollment} for TOTP, {@link WebAuthnCredential} for Passkeys).
 */
@Entity
@Table(name = "user_mfa_factors",
        indexes = {
                @Index(name = "idx_user_mfa_factors_user", columnList = "user_id"),
                @Index(name = "idx_user_mfa_factors_status", columnList = "status"),
                @Index(name = "idx_user_mfa_factors_type", columnList = "factor_type")
        })
public class UserMfaFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "factor_type", nullable = false, length = 20)
    private MfaFactorType factorType;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MfaFactorStatus status = MfaFactorStatus.PENDING;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected UserMfaFactor() {
    }

    public UserMfaFactor(UUID userId, UUID organizationId, MfaFactorType factorType, String displayName) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.factorType = factorType;
        this.displayName = displayName;
        this.status = MfaFactorStatus.PENDING;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getOrganizationId() { return organizationId; }
    public MfaFactorType getFactorType() { return factorType; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public MfaFactorStatus getStatus() { return status; }
    public void setStatus(MfaFactorStatus status) { this.status = status; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public boolean isActive() { return status == MfaFactorStatus.ACTIVE; }
}
