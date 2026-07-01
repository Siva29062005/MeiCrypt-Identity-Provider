package com.meicrypt.identity.admin.dto;

import com.meicrypt.identity.organization.entity.OrganizationStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Platform-admin state transition request (Phase 12).
 */
public record UpdateOrganizationStatusRequest(
        @NotNull OrganizationStatus status,
        String reason) {
}
