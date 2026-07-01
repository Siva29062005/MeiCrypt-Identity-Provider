package com.meicrypt.identity.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DeviceDTO(
        UUID id,
        UUID userId,
        String deviceFingerprint,
        String deviceName,
        String deviceType,
        String browser,
        String operatingSystem,
        String lastIpAddress,
        LocalDateTime lastSeenAt,
        Boolean trusted,
        LocalDateTime createdAt
) {}
