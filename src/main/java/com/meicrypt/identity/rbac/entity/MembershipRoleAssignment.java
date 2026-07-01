package com.meicrypt.identity.rbac.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * MembershipRoleAssignment Entity - Links an {@code OrganizationMembership} to a {@link Role}.
 *
 * A membership can hold multiple roles; the union of their permissions is the
 * effective set granted to the underlying user inside the organization.
 *
 * Enforcement rule (validated in service layer): the role must belong to the
 * same organization as the membership. This preserves strict multi-tenant
 * isolation across the RBAC boundary.
 */
@Entity
@Table(name = "membership_role_assignments",
        uniqueConstraints = {
                @UniqueConstraint(name = "unique_membership_role",
                        columnNames = {"membership_id", "role_id"})
        },
        indexes = {
                @Index(name = "idx_mra_membership_id", columnList = "membership_id"),
                @Index(name = "idx_mra_role_id", columnList = "role_id")
        })
public class MembershipRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "assigned_at", nullable = false, updatable = false, insertable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by_user_id")
    private UUID assignedByUserId;

    protected MembershipRoleAssignment() {
    }

    public MembershipRoleAssignment(UUID membershipId, UUID roleId, UUID assignedByUserId) {
        this.membershipId = membershipId;
        this.roleId = roleId;
        this.assignedByUserId = assignedByUserId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public UUID getAssignedByUserId() {
        return assignedByUserId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MembershipRoleAssignment that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
