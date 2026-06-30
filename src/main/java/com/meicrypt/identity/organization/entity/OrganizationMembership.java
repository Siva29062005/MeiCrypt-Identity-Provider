package com.meicrypt.identity.organization.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * OrganizationMembership entity - Links users to organizations with specific roles.
 * This is the intersection entity managing the many-to-many relationship between
 * users and organizations, with additional role and status information.
 * 
 * Ensures proper multi-tenant isolation by tracking which users belong to which organizations.
 */
@Entity
@Table(name = "organization_memberships", 
       uniqueConstraints = {
           @UniqueConstraint(name = "unique_organization_user_membership", 
                           columnNames = {"organization_id", "user_id"})
       },
       indexes = {
           @Index(name = "idx_organization_memberships_organization_id", columnList = "organization_id"),
           @Index(name = "idx_organization_memberships_user_id", columnList = "user_id"),
           @Index(name = "idx_organization_memberships_status", columnList = "status"),
           @Index(name = "idx_organization_memberships_role", columnList = "role")
       })
@EntityListeners(AuditingEntityListener.class)
public class OrganizationMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Default constructor required by JPA.
     */
    protected OrganizationMembership() {
        // JPA requires a no-arg constructor
    }

    /**
     * Constructor with required fields following zero-field-injection principle.
     *
     * @param organizationId The organization ID
     * @param userId         The user ID
     * @param role          The membership role
     * @param status        The membership status
     */
    public OrganizationMembership(UUID organizationId, UUID userId, 
                                 MembershipRole role, MembershipStatus status) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.joinedAt = Instant.now();
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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public MembershipRole getRole() {
        return role;
    }

    public void setRole(MembershipRole role) {
        this.role = role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrganizationMembership that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "OrganizationMembership{" +
                "id=" + id +
                ", organizationId=" + organizationId +
                ", userId=" + userId +
                ", role=" + role +
                ", status=" + status +
                ", joinedAt=" + joinedAt +
                '}';
    }
}
