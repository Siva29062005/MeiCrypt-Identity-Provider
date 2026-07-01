package com.meicrypt.identity.rbac.mapper;

import com.meicrypt.identity.rbac.dto.PermissionDTO;
import com.meicrypt.identity.rbac.dto.RoleAssignmentDTO;
import com.meicrypt.identity.rbac.dto.RoleDTO;
import com.meicrypt.identity.rbac.entity.MembershipRoleAssignment;
import com.meicrypt.identity.rbac.entity.Permission;
import com.meicrypt.identity.rbac.entity.Role;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Manual mapper for RBAC entities <-> DTOs.
 *
 * We do not use MapStruct here because the mapping is trivial and to keep
 * the RBAC module self-contained (Section 1 - Domain Boundary Isolation).
 */
@Component
public class RbacMapper {

    public PermissionDTO toDTO(Permission permission) {
        return new PermissionDTO(
                permission.getId(),
                permission.getCode(),
                permission.getDomain(),
                permission.getResource(),
                permission.getAction(),
                permission.getDescription(),
                permission.isSystem()
        );
    }

    public RoleDTO toDTO(Role role) {
        Set<Permission> permissions = role.getPermissions();
        List<String> codes = permissions == null
                ? Collections.emptyList()
                : permissions.stream().map(Permission::getCode).sorted().toList();
        return new RoleDTO(
                role.getId(),
                role.getOrganizationId(),
                role.getName(),
                role.getSlug(),
                role.getDescription(),
                role.getRoleType(),
                role.isDefaultRole(),
                codes,
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }

    public RoleAssignmentDTO toDTO(MembershipRoleAssignment assignment, Role role) {
        return new RoleAssignmentDTO(
                assignment.getId(),
                assignment.getMembershipId(),
                assignment.getRoleId(),
                role != null ? role.getName() : null,
                role != null ? role.getSlug() : null,
                assignment.getAssignedAt(),
                assignment.getAssignedByUserId()
        );
    }
}
