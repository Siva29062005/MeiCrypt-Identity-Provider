package com.meicrypt.identity.auth.dto;

import com.meicrypt.identity.auth.entity.SessionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionDTO(
        UUID id,
        UUID userId,
        UUID organizationId,
        UUID deviceId,
        String ipAddress,
        String userAgent,
        SessionStatus status,
        LocalDateTime createdAt,
        LocalDateTime lastActivityAt,
        LocalDateTime expiresAt
) {}
