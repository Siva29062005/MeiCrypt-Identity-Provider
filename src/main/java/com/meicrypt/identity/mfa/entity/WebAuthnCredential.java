package com.meicrypt.identity.mfa.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Registered WebAuthn/Passkey credential (Module 9.2). Only public key
 * material + counters are stored; the private key never leaves the
 * authenticator.
 */
@Entity
@Table(name = "webauthn_credentials",
        indexes = {
                @Index(name = "idx_webauthn_credentials_factor", columnList = "factor_id"),
                @Index(name = "idx_webauthn_credentials_credential_id", columnList = "credential_id")
        })
public class WebAuthnCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "factor_id", nullable = false, unique = true)
    private UUID factorId;

    @Column(name = "credential_id", nullable = false, unique = true, length = 500)
    private String credentialId;

    @Column(name = "attestation_object_b64", nullable = false, columnDefinition = "TEXT")
    private String attestationObjectBase64;

    @Column(name = "public_key_cose_b64", nullable = false, columnDefinition = "TEXT")
    private String publicKeyCoseBase64;

    @Column(name = "aaguid", length = 64)
    private String aaguid;

    @Column(name = "sign_count", nullable = false)
    private long signCount = 0L;

    @Column(name = "transports", length = 200)
    private String transports;

    @Column(name = "user_verified", nullable = false)
    private boolean userVerified;

    @Column(name = "backup_eligible", nullable = false)
    private boolean backupEligible;

    @Column(name = "backup_state", nullable = false)
    private boolean backupState;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected WebAuthnCredential() {
    }

    public WebAuthnCredential(UUID factorId, String credentialId,
                              String attestationObjectBase64, String publicKeyCoseBase64) {
        this.factorId = factorId;
        this.credentialId = credentialId;
        this.attestationObjectBase64 = attestationObjectBase64;
        this.publicKeyCoseBase64 = publicKeyCoseBase64;
    }

    public UUID getId() { return id; }
    public UUID getFactorId() { return factorId; }
    public String getCredentialId() { return credentialId; }
    public String getAttestationObjectBase64() { return attestationObjectBase64; }
    public String getPublicKeyCoseBase64() { return publicKeyCoseBase64; }
    public String getAaguid() { return aaguid; }
    public void setAaguid(String aaguid) { this.aaguid = aaguid; }
    public long getSignCount() { return signCount; }
    public void setSignCount(long signCount) { this.signCount = signCount; }
    public String getTransports() { return transports; }
    public void setTransports(String transports) { this.transports = transports; }
    public boolean isUserVerified() { return userVerified; }
    public void setUserVerified(boolean userVerified) { this.userVerified = userVerified; }
    public boolean isBackupEligible() { return backupEligible; }
    public void setBackupEligible(boolean backupEligible) { this.backupEligible = backupEligible; }
    public boolean isBackupState() { return backupState; }
    public void setBackupState(boolean backupState) { this.backupState = backupState; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
