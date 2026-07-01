package com.meicrypt.identity.application.dto;

import com.meicrypt.identity.application.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Lifecycle transition request - ACTIVE / SUSPENDED / REVOKED.
 */
public record UpdateApplicationStatusRequest(
        @NotNull ApplicationStatus status
) {}
