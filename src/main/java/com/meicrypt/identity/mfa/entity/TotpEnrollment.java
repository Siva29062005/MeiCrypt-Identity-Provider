package com.meicrypt.identity.mfa.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * RFC 6238 TOTP secret + configuration for a single {@link UserMfaFactor}.
 * Anti-replay is enforced by tracking {@code lastUsedCounter} - the 30-second
 * window index of the last accepted code.
 */
@Entity
@Table(name = "totp_enrollments",
        indexes = {
                @Index(name = "idx_totp_enrollments_factor", columnList = "factor_id")
        })
public class TotpEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "factor_id", nullable = false, unique = true)
    private UUID factorId;

    @Column(name = "secret_base32", nullable = false, length = 128)
    private String secretBase32;

    @Column(name = "algorithm", nullable = false, length = 10)
    private String algorithm = "SHA1";

    @Column(name = "digits", nullable = false)
    private int digits = 6;

    @Column(name = "period_seconds", nullable = false)
    private int periodSeconds = 30;

    @Column(name = "issuer", nullable = false, length = 120)
    private String issuer;

    @Column(name = "account_label", nullable = false, length = 255)
    private String accountLabel;

    @Column(name = "last_used_counter")
    private Long lastUsedCounter;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TotpEnrollment() {
    }

    public TotpEnrollment(UUID factorId, String secretBase32, String issuer, String accountLabel) {
        this.factorId = factorId;
        this.secretBase32 = secretBase32;
        this.issuer = issuer;
        this.accountLabel = accountLabel;
    }

    public UUID getId() { return id; }
    public UUID getFactorId() { return factorId; }
    public String getSecretBase32() { return secretBase32; }
    public String getAlgorithm() { return algorithm; }
    public int getDigits() { return digits; }
    public int getPeriodSeconds() { return periodSeconds; }
    public String getIssuer() { return issuer; }
    public String getAccountLabel() { return accountLabel; }
    public Long getLastUsedCounter() { return lastUsedCounter; }
    public void setLastUsedCounter(Long lastUsedCounter) { this.lastUsedCounter = lastUsedCounter; }
    public Instant getCreatedAt() { return createdAt; }
}
