package com.meicrypt.identity.rbac.service;

import com.meicrypt.identity.common.exception.DuplicateResourceException;
import com.meicrypt.identity.organization.repository.OrganizationRepository;
import com.meicrypt.identity.rbac.dto.CreateRoleRequest;
import com.meicrypt.identity.rbac.dto.RoleDTO;
import com.meicrypt.identity.rbac.dto.UpdateRoleRequest;
import com.meicrypt.identity.rbac.entity.Permission;
import com.meicrypt.identity.rbac.entity.Role;
import com.meicrypt.identity.rbac.entity.RoleType;
import com.meicrypt.identity.rbac.exception.ImmutableRoleException;
import com.meicrypt.identity.rbac.exception.RoleNotFoundException;
import com.meicrypt.identity.rbac.mapper.RbacMapper;
import com.meicrypt.identity.rbac.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Role management (Module 4.2).
 *
 * All operations are scoped to a single organization. Attempts to touch a
 * SYSTEM role are rejected with {@link ImmutableRoleException} to preserve the
 * platform's guarantee that OWNER/ADMIN/MEMBER seeded roles remain stable.
 */
@Service
@Transactional(readOnly = true)
public class RoleService {

    private static final Logger logger = LoggerFactory.getLogger(RoleService.class);

    private final RoleRepository roleRepository;
    private final PermissionService permissionService;
    private final RbacMapper rbacMapper;
    private final OrganizationRepository organizationRepository;

    public RoleService(RoleRepository roleRepository,
                       PermissionService permissionService,
                       RbacMapper rbacMapper,
                       OrganizationRepository organizationRepository) {
        this.roleRepository = roleRepository;
        this.permissionService = permissionService;
        this.rbacMapper = rbacMapper;
        this.organizationRepository = organizationRepository;
    }

    public List<RoleDTO> listRoles(UUID organizationId) {
        requireOrganizationExists(organizationId);
        return roleRepository.findByOrganizationId(organizationId)
                .stream()
                .map(rbacMapper::toDTO)
                .toList();
    }

    public RoleDTO getRole(UUID organizationId, UUID roleId) {
        Role role = requireRole(organizationId, roleId);
        return rbacMapper.toDTO(role);
    }

    @Transactional
    public RoleDTO createRole(UUID organizationId, CreateRoleRequest request) {
        requireOrganizationExists(organizationId);
        String slug = normalizeSlug(request.slug(), request.name());
        if (roleRepository.existsByOrganizationIdAndSlug(organizationId, slug)) {
            throw new DuplicateResourceException("Role", "slug", slug);
        }

        Role role = new Role(
                organizationId,
                request.name(),
                slug,
                request.description(),
                RoleType.CUSTOM,
                request.defaultRole()
        );
        Set<Permission> permissions = permissionService.resolveByCodes(request.permissionCodes());
        role.setPermissions(permissions);

        Role saved = roleRepository.save(role);
        logger.info("Created role {} ({}) in organization {}", saved.getId(), saved.getSlug(), organizationId);
        return rbacMapper.toDTO(saved);
    }

    @Transactional
    public RoleDTO updateRole(UUID organizationId, UUID roleId, UpdateRoleRequest request) {
        Role role = requireRole(organizationId, roleId);
        if (role.isSystemRole()) {
            throw new ImmutableRoleException("System roles cannot be modified");
        }

        if (request.name() != null) {
            role.setName(request.name());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        if (request.defaultRole() != null) {
            role.setDefaultRole(request.defaultRole());
        }
        if (request.permissionCodes() != null) {
            Set<Permission> permissions = permissionService.resolveByCodes(request.permissionCodes());
            role.setPermissions(permissions);
        }

        Role saved = roleRepository.save(role);
        logger.info("Updated role {} in organization {}", saved.getId(), organizationId);
        return rbacMapper.toDTO(saved);
    }

    @Transactional
    public void deleteRole(UUID organizationId, UUID roleId) {
        Role role = requireRole(organizationId, roleId);
        if (role.isSystemRole()) {
            throw new ImmutableRoleException("System roles cannot be deleted");
        }
        roleRepository.delete(role);
        logger.info("Deleted role {} in organization {}", roleId, organizationId);
    }

    /* Package-private helpers used by assignment service */

    Role requireRole(UUID organizationId, UUID roleId) {
        return roleRepository.findByIdAndOrganizationId(roleId, organizationId)
                .orElseThrow(() -> new RoleNotFoundException(
                        "Role " + roleId + " not found in organization " + organizationId));
    }

    private void requireOrganizationExists(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new RoleNotFoundException("Organization " + organizationId + " does not exist");
        }
    }

    private String normalizeSlug(String slug, String name) {
        String source = (slug == null || slug.isBlank()) ? name : slug;
        String normalized = source.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Cannot derive a valid slug from '" + source + "'");
        }
        return normalized;
    }
}
