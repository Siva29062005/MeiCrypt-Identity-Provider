package com.meicrypt.identity.organization.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * OrganizationCustomDomain entity - Manages custom domain bindings for organizations.
 * Enables enterprise identity federation by allowing organizations to use their own domains.
 * 
 * Supports multiple verification methods and tracks verification status.
 */
@Entity
@Table(name = "organization_custom_domains",
       uniqueConstraints = {
           @UniqueConstraint(name = "unique_domain", columnNames = "domain")
       },
       indexes = {
           @Index(name = "idx_organization_custom_domains_organization_id", columnList = "organization_id"),
           @Index(name = "idx_organization_custom_domains_domain", columnList = "domain"),
           @Index(name = "idx_organization_custom_domains_verification_status", columnList = "verification_status"),
           @Index(name = "idx_organization_custom_domains_is_primary", columnList = "is_primary")
       })
@EntityListeners(AuditingEntityListener.class)
public class OrganizationCustomDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "domain", nullable = false, unique = true, length = 255)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private DomainVerificationStatus verificationStatus;

    @Column(name = "verification_token", nullable = false, unique = true, length = 500)
    private String verificationToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", nullable = false, length = 50)
    private DomainVerificationMethod verificationMethod;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Default constructor required by JPA.
     */
    protected OrganizationCustomDomain() {
        // JPA requires a no-arg constructor
    }

    /**
     * Constructor with required fields following zero-field-injection principle.
     *
     * @param organizationId      The organization ID
     * @param domain             The custom domain name
     * @param verificationToken  The verification token
     * @param verificationMethod The verification method
     */
    public OrganizationCustomDomain(UUID organizationId, String domain, 
                                   String verificationToken, DomainVerificationMethod verificationMethod) {
        this.organizationId = organizationId;
        this.domain = domain;
        this.verificationStatus = DomainVerificationStatus.PENDING;
        this.verificationToken = verificationToken;
        this.verificationMethod = verificationMethod;
        this.isPrimary = false;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public DomainVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(DomainVerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public DomainVerificationMethod getVerificationMethod() {
        return verificationMethod;
    }

    public void setVerificationMethod(DomainVerificationMethod verificationMethod) {
        this.verificationMethod = verificationMethod;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Check if the domain is verified.
     *
     * @return true if verified
     */
    public boolean isVerified() {
        return verificationStatus == DomainVerificationStatus.VERIFIED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrganizationCustomDomain that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "OrganizationCustomDomain{" +
                "id=" + id +
                ", organizationId=" + organizationId +
                ", domain='" + domain + '\'' +
                ", verificationStatus=" + verificationStatus +
                ", verificationMethod=" + verificationMethod +
                ", isPrimary=" + isPrimary +
                '}';
    }
}
