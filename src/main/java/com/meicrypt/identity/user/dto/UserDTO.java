package com.meicrypt.identity.user.dto;

import com.meicrypt.identity.user.entity.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for User entity.
 * Used for API responses (excludes sensitive data like password hash).
 */
public record UserDTO(
    UUID id,
    UUID organizationId,
    String email,
    Boolean emailVerified,
    String firstName,
    String lastName,
    String displayName,
    String profilePictureUrl,
    String phoneNumber,
    Boolean phoneVerified,
    String locale,
    String timezone,
    UserStatus status,
    LocalDateTime lastLoginAt,
    String lastLoginIp,
    LocalDateTime passwordChangedAt,
    Integer failedLoginAttempts,
    LocalDateTime lockedUntil,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
