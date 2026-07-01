package com.meicrypt.identity.rbac.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a {@link com.meicrypt.identity.rbac.entity.MembershipRoleAssignment}.
 */
public record RoleAssignmentDTO(
        UUID id,
        UUID membershipId,
        UUID roleId,
        String roleName,
        String roleSlug,
        Instant assignedAt,
        UUID assignedByUserId
) {}
