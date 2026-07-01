package com.meicrypt.identity.oauth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent RSA signing key entry (Phase 7, Module 7.2).
 *
 * <p>Private key material is stored PKCS#8/base64-encoded; public key material
 * is stored as X.509 SubjectPublicKeyInfo/base64. Production deployments MUST
 * source these values from a KMS/HSM - this table exists so local and CI
 * environments start with self-contained state.
 */
@Entity
@Table(name = "oauth_signing_keys",
        indexes = {
                @Index(name = "idx_oauth_signing_keys_status", columnList = "status")
        })
public class OAuthSigningKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "kid", nullable = false, unique = true, length = 64)
    private String kid;

    @Column(name = "algorithm", nullable = false, length = 20)
    private String algorithm = "RS256";

    @Column(name = "key_type", nullable = false, length = 10)
    private String keyType = "RSA";

    @Column(name = "key_use", nullable = false, length = 10)
    private String keyUse = "sig";

    @Column(name = "private_key_pkcs8_b64", nullable = false, columnDefinition = "TEXT")
    private String privateKeyPkcs8Base64;

    @Column(name = "public_key_x509_b64", nullable = false, columnDefinition = "TEXT")
    private String publicKeyX509Base64;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OAuthSigningKeyStatus status = OAuthSigningKeyStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected OAuthSigningKey() {
    }

    public OAuthSigningKey(String kid, String privateKeyPkcs8Base64, String publicKeyX509Base64) {
        this.kid = kid;
        this.privateKeyPkcs8Base64 = privateKeyPkcs8Base64;
        this.publicKeyX509Base64 = publicKeyX509Base64;
        this.status = OAuthSigningKeyStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public String getKid() { return kid; }
    public String getAlgorithm() { return algorithm; }
    public String getKeyType() { return keyType; }
    public String getKeyUse() { return keyUse; }
    public String getPrivateKeyPkcs8Base64() { return privateKeyPkcs8Base64; }
    public String getPublicKeyX509Base64() { return publicKeyX509Base64; }
    public OAuthSigningKeyStatus getStatus() { return status; }
    public void setStatus(OAuthSigningKeyStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Instant rotatedAt) { this.rotatedAt = rotatedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public boolean isActive() { return status == OAuthSigningKeyStatus.ACTIVE; }
    public boolean isPubliclyVisible() { return status != OAuthSigningKeyStatus.REVOKED; }
}
