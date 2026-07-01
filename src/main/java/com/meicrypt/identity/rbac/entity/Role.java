package com.meicrypt.identity.rbac.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Role Entity - Organization-scoped grouping of permissions.
 *
 * A role is always tied to a single organization (multi-tenant isolation).
 * Two flavors exist:
 * <ul>
 *   <li>{@link RoleType#SYSTEM} - auto-provisioned per org (OWNER, ADMIN, MEMBER).
 *       These roles are immutable and cannot be deleted through the API.</li>
 *   <li>{@link RoleType#CUSTOM} - defined by organization administrators.</li>
 * </ul>
 *
 * A role can be marked {@code isDefault} to be assigned automatically when a
 * new membership joins the organization.
 */
@Entity
@Table(name = "roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "unique_role_slug_per_org",
                        columnNames = {"organization_id", "slug"})
        },
        indexes = {
                @Index(name = "idx_roles_organization_id", columnList = "organization_id"),
                @Index(name = "idx_roles_type", columnList = "role_type"),
                @Index(name = "idx_roles_slug", columnList = "slug")
        })
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 20)
    private RoleType roleType = RoleType.CUSTOM;

    @Column(name = "is_default", nullable = false)
    private boolean defaultRole = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Owning side of the role<->permission relation.
     * Uses eager fetch through JPQL / repository queries; lazy by default.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "unique_role_permission",
                    columnNames = {"role_id", "permission_id"})
    )
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
    }

    public Role(UUID organizationId, String name, String slug, String description,
                RoleType roleType, boolean defaultRole) {
        this.organizationId = organizationId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.roleType = roleType;
        this.defaultRole = defaultRole;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RoleType getRoleType() {
        return roleType;
    }

    public boolean isDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(boolean defaultRole) {
        this.defaultRole = defaultRole;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isSystemRole() {
        return roleType == RoleType.SYSTEM;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
