package com.meicrypt.identity.admin.dto;

/**
 * Platform-wide health / footprint snapshot returned by
 * {@code GET /api/v1/admin/stats} (Phase 12).
 */
public record PlatformStatsDTO(
        long organizationCount,
        long activeOrganizationCount,
        long userCount,
        long activeSessionCount,
        long pendingNotifications,
        long clientApplicationCount) {
}
