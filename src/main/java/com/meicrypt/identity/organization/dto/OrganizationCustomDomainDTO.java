package com.meicrypt.identity.organization.dto;

import com.meicrypt.identity.organization.entity.DomainVerificationMethod;
import com.meicrypt.identity.organization.entity.DomainVerificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for organization custom domain information.
 * Used for API responses when returning domain details.
 */
public record OrganizationCustomDomainDTO(
    UUID id,
    UUID organizationId,
    String domain,
    DomainVerificationStatus verificationStatus,
    DomainVerificationMethod verificationMethod,
    Instant verifiedAt,
    Boolean isPrimary,
    Instant createdAt,
    Instant updatedAt
) {
}
