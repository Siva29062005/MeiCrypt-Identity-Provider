package com.meicrypt.identity.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload to create a new custom role inside an organization.
 * The slug will be auto-derived from the name if omitted.
 */
public record CreateRoleRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 100)
        String slug,

        @Size(max = 500)
        String description,

        boolean defaultRole,

        List<String> permissionCodes
) {}
