package com.meicrypt.identity.admin.dto;

import com.meicrypt.identity.organization.entity.Organization;
import com.meicrypt.identity.organization.entity.OrganizationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform-admin projection of an organization (Phase 12 - Module 12.1).
 * Surfaces the minimum information needed on the admin console dashboard.
 */
public record PlatformOrganizationSummaryDTO(
        UUID id,
        String name,
        String slug,
        OrganizationStatus status,
        long userCount,
        long activeSessionCount,
        Instant createdAt) {

    public static PlatformOrganizationSummaryDTO of(Organization org, long userCount, long sessionCount) {
        return new PlatformOrganizationSummaryDTO(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getStatus(),
                userCount,
                sessionCount,
                org.getCreatedAt());
    }
}
