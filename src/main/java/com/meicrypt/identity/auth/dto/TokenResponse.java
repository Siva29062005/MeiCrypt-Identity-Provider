package com.meicrypt.identity.auth.dto;

import java.util.UUID;

/**
 * Token pair issued on successful authentication or refresh.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID sessionId,
        UUID userId
) {
    public static TokenResponse of(String accessToken, String refreshToken,
                                   long expiresIn, UUID sessionId, UUID userId) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, sessionId, userId);
    }
}
