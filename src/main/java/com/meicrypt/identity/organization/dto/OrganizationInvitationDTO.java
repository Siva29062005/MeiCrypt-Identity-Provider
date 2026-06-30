package com.meicrypt.identity.organization.dto;

import com.meicrypt.identity.organization.entity.InvitationStatus;
import com.meicrypt.identity.organization.entity.MembershipRole;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for organization invitation information.
 * Used for API responses when returning invitation details.
 */
public record OrganizationInvitationDTO(
    UUID id,
    UUID organizationId,
    String email,
    UUID invitedByUserId,
    MembershipRole role,
    InvitationStatus status,
    Instant expiresAt,
    Instant acceptedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
