package com.meicrypt.identity.rbac.dto;

import java.util.UUID;

/**
 * Public representation of a {@link com.meicrypt.identity.rbac.entity.Permission}.
 */
public record PermissionDTO(
        UUID id,
        String code,
        String domain,
        String resource,
        String action,
        String description,
        boolean system
) {}
