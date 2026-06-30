package com.meicrypt.identity.organization.dto;

import com.meicrypt.identity.organization.entity.MembershipRole;
import com.meicrypt.identity.organization.entity.MembershipStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for organization membership information.
 * Used for API responses when returning membership details.
 */
public record OrganizationMembershipDTO(
    UUID id,
    UUID organizationId,
    UUID userId,
    MembershipRole role,
    MembershipStatus status,
    Instant joinedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
