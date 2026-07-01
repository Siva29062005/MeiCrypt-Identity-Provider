package com.meicrypt.identity.rbac.dto;

import com.meicrypt.identity.rbac.entity.RoleType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public representation of a role, including the codes of the permissions
 * attached to it.
 */
public record RoleDTO(
        UUID id,
        UUID organizationId,
        String name,
        String slug,
        String description,
        RoleType roleType,
        boolean defaultRole,
        List<String> permissionCodes,
        Instant createdAt,
        Instant updatedAt
) {}
