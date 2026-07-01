package com.meicrypt.identity.rbac.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Permission Entity - System-defined authorization primitive.
 *
 * Permissions are global (not org-scoped): they represent the platform's
 * vocabulary of "what can be done", encoded as {@code domain:resource:action}.
 * Only {@link Role}s are organization-scoped, and roles bundle permissions.
 *
 * These records are seeded via Flyway (V5) and are immutable at runtime
 * (isSystem = true). Additional non-system permissions may be introduced by
 * future migrations only — never by user input.
 */
@Entity
@Table(name = "permissions", indexes = {
        @Index(name = "idx_permissions_domain", columnList = "domain"),
        @Index(name = "idx_permissions_resource", columnList = "resource"),
        @Index(name = "idx_permissions_action", columnList = "action")
})
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 150)
    private String code;

    @Column(name = "domain", nullable = false, length = 50)
    private String domain;

    @Column(name = "resource", nullable = false, length = 50)
    private String resource;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean system = true;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected Permission() {
    }

    public Permission(String code, String domain, String resource, String action,
                      String description, boolean system) {
        this.code = code;
        this.domain = domain;
        this.resource = resource;
        this.action = action;
        this.description = description;
        this.system = system;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDomain() {
        return domain;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystem() {
        return system;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permission that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
