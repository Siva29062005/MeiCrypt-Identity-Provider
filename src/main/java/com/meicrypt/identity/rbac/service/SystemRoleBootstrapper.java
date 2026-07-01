package com.meicrypt.identity.rbac.service;

import com.meicrypt.identity.rbac.entity.Permission;
import com.meicrypt.identity.rbac.entity.Role;
import com.meicrypt.identity.rbac.entity.RoleType;
import com.meicrypt.identity.rbac.repository.PermissionRepository;
import com.meicrypt.identity.rbac.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Provisions the OWNER / ADMIN / MEMBER SYSTEM roles for a new organization.
 *
 * <p>The Flyway migration V6 handles backfill for existing organizations.
 * This service is invoked programmatically by
 * {@link com.meicrypt.identity.organization.service.OrganizationService}
 * whenever a new organization is created, so the same baseline is preserved.
 *
 * <p>The service is idempotent: running it twice for the same organization
 * leaves the schema unchanged.
 */
@Service
public class SystemRoleBootstrapper {

    private static final Logger logger = LoggerFactory.getLogger(SystemRoleBootstrapper.class);

    private static final String OWNER_SLUG = "owner";
    private static final String ADMIN_SLUG = "admin";
    private static final String MEMBER_SLUG = "member";

    private static final Set<String> ADMIN_PERMISSIONS = Set.of(
            "organization:organization:read",
            "organization:organization:update",
            "organization:membership:read",
            "organization:membership:manage",
            "organization:invitation:read",
            "organization:invitation:manage",
            "organization:domain:read",
            "organization:domain:manage",
            "identity:user:read",
            "identity:user:update",
            "identity:user:suspend",
            "identity:session:read",
            "identity:session:revoke",
            "rbac:role:read",
            "rbac:role:manage",
            "rbac:permission:read",
            "rbac:assignment:read",
            "rbac:assignment:manage",
            "oauth:application:read",
            "oauth:application:manage",
            "audit:log:read"
    );

    private static final Set<String> MEMBER_PERMISSIONS = Set.of(
            "organization:organization:read",
            "organization:membership:read",
            "rbac:role:read",
            "rbac:permission:read"
    );

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public SystemRoleBootstrapper(RoleRepository roleRepository,
                                  PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional
    public void bootstrap(UUID organizationId) {
        List<Permission> allPermissions = permissionRepository.findAll();
        Set<Permission> adminPerms = filterByCodes(allPermissions, ADMIN_PERMISSIONS);
        Set<Permission> memberPerms = filterByCodes(allPermissions, MEMBER_PERMISSIONS);

        upsertRole(organizationId, "Owner", OWNER_SLUG,
                "Full control over the organization, including billing and ownership transfer.",
                false, new HashSet<>(allPermissions));
        upsertRole(organizationId, "Administrator", ADMIN_SLUG,
                "Manage members, roles, applications and organization settings.",
                false, adminPerms);
        upsertRole(organizationId, "Member", MEMBER_SLUG,
                "Standard organization member with baseline read access.",
                true, memberPerms);

        logger.info("Bootstrapped SYSTEM roles for organization {}", organizationId);
    }

    private void upsertRole(UUID organizationId, String name, String slug, String description,
                            boolean defaultRole, Set<Permission> permissions) {
        Role role = roleRepository.findByOrganizationIdAndSlug(organizationId, slug)
                .orElse(null);
        if (role == null) {
            role = new Role(organizationId, name, slug, description, RoleType.SYSTEM, defaultRole);
            role.setPermissions(new HashSet<>(permissions));
            roleRepository.save(role);
            return;
        }
        // Idempotent update: ensure permission set is aligned even if the catalog grew
        role.setPermissions(new HashSet<>(permissions));
        roleRepository.save(role);
    }

    private Set<Permission> filterByCodes(List<Permission> all, Set<String> codes) {
        Set<Permission> filtered = new HashSet<>();
        for (Permission p : all) {
            if (codes.contains(p.getCode())) {
                filtered.add(p);
            }
        }
        return filtered;
    }
}
