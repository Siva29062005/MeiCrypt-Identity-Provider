package com.meicrypt.identity.rbac.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload to update a custom role. Only non-null fields are applied.
 * The role slug and type cannot be changed after creation.
 */
public record UpdateRoleRequest(
        @Size(max = 100) String name,
        @Size(max = 500) String description,
        Boolean defaultRole,
        List<String> permissionCodes
) {}
