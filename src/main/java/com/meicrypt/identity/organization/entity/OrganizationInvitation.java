package com.meicrypt.identity.organization.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * OrganizationInvitation entity - Manages secure multi-stage invitation workflows.
 * Allows organization owners to dispatch invitations with unique tokens and expiry thresholds.
 * 
 * Supports secure onboarding of new members to organizations.
 */
@Entity
@Table(name = "organization_invitations",
       indexes = {
           @Index(name = "idx_organization_invitations_organization_id", columnList = "organization_id"),
           @Index(name = "idx_organization_invitations_email", columnList = "email"),
           @Index(name = "idx_organization_invitations_token", columnList = "invitation_token"),
           @Index(name = "idx_organization_invitations_status", columnList = "status"),
           @Index(name = "idx_organization_invitations_expires_at", columnList = "expires_at")
       })
@EntityListeners(AuditingEntityListener.class)
public class OrganizationInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "invited_by_user_id")
    private UUID invitedByUserId;

    @Column(name = "invitation_token", nullable = false, unique = true, length = 500)
    private String invitationToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Default constructor required by JPA.
     */
    protected OrganizationInvitation() {
        // JPA requires a no-arg constructor
    }

    /**
     * Constructor with required fields following zero-field-injection principle.
     *
     * @param organizationId   The organization ID
     * @param email           The invitee's email address
     * @param invitedByUserId The user ID of the inviter
     * @param invitationToken The secure invitation token
     * @param role            The role to assign upon acceptance
     * @param expiresAt       The expiration timestamp
     */
    public OrganizationInvitation(UUID organizationId, String email, UUID invitedByUserId,
                                 String invitationToken, MembershipRole role, Instant expiresAt) {
        this.organizationId = organizationId;
        this.email = email;
        this.invitedByUserId = invitedByUserId;
        this.invitationToken = invitationToken;
        this.role = role;
        this.status = InvitationStatus.PENDING;
        this.expiresAt = expiresAt;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    public void setInvitedByUserId(UUID invitedByUserId) {
        this.invitedByUserId = invitedByUserId;
    }

    public String getInvitationToken() {
        return invitationToken;
    }

    public void setInvitationToken(String invitationToken) {
        this.invitationToken = invitationToken;
    }

    public MembershipRole getRole() {
        return role;
    }

    public void setRole(MembershipRole role) {
        this.role = role;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Check if the invitation is expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if the invitation is still pending and valid.
     *
     * @return true if pending and not expired
     */
    public boolean isValid() {
        return status == InvitationStatus.PENDING && !isExpired();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrganizationInvitation that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "OrganizationInvitation{" +
                "id=" + id +
                ", organizationId=" + organizationId +
                ", email='" + email + '\'' +
                ", role=" + role +
                ", status=" + status +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
