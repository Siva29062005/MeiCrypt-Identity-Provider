package com.meicrypt.identity.rbac.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload to assign a role to an organization membership.
 */
public record AssignRoleRequest(
        @NotNull UUID roleId
) {}
