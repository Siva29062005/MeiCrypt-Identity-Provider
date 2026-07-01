package com.meicrypt.identity.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT + session tuning parameters loaded from application.yml under `meicrypt.auth`.
 */
@ConfigurationProperties(prefix = "meicrypt.auth")
public record JwtProperties(
        String issuer,
        String secret,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds,
        long sessionTtlSeconds
) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "meicrypt-identity";
        }
        if (accessTokenTtlSeconds <= 0) accessTokenTtlSeconds = 900L;          // 15 min
        if (refreshTokenTtlSeconds <= 0) refreshTokenTtlSeconds = 604800L;     // 7 days
        if (sessionTtlSeconds <= 0) sessionTtlSeconds = 28800L;                // 8 hours
    }
}
