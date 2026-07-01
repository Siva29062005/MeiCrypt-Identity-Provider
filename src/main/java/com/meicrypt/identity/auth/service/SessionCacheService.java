package com.meicrypt.identity.auth.service;

import com.meicrypt.identity.auth.config.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Fast-path lookup for live session state.
 * Redis is optional at boot - if not configured, this service degrades gracefully.
 *
 * Keys:
 *   session:{sessionId}     -> "ACTIVE" (with TTL)
 *   revoked:jti:{jti}       -> "1"      (until access token expiry)
 */
@Service
public class SessionCacheService {

    private static final Logger logger = LoggerFactory.getLogger(SessionCacheService.class);
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String REVOKED_JTI_PREFIX = "revoked:jti:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    @Autowired(required = false)
    public SessionCacheService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    public void registerSession(UUID sessionId) {
        if (redisTemplate == null) return;
        try {
            redisTemplate.opsForValue().set(
                    SESSION_KEY_PREFIX + sessionId,
                    "ACTIVE",
                    Duration.ofSeconds(jwtProperties.sessionTtlSeconds()));
        } catch (Exception ex) {
            logger.warn("Failed to register session in Redis: {}", ex.getMessage());
        }
    }

    public boolean isSessionActive(UUID sessionId) {
        if (redisTemplate == null) return true; // fail-open in dev without Redis
        try {
            String value = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + sessionId);
            return "ACTIVE".equals(value);
        } catch (Exception ex) {
            logger.warn("Redis lookup failed for session {}: {}", sessionId, ex.getMessage());
            return true;
        }
    }

    public void terminateSession(UUID sessionId) {
        if (redisTemplate == null) return;
        try {
            redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
        } catch (Exception ex) {
            logger.warn("Failed to terminate session in Redis: {}", ex.getMessage());
        }
    }

    public void touchSession(UUID sessionId) {
        if (redisTemplate == null) return;
        try {
            redisTemplate.expire(SESSION_KEY_PREFIX + sessionId,
                    Duration.ofSeconds(jwtProperties.sessionTtlSeconds()));
        } catch (Exception ex) {
            logger.debug("Failed to refresh session TTL: {}", ex.getMessage());
        }
    }

    public void blacklistAccessToken(String jti, long remainingSeconds) {
        if (redisTemplate == null || remainingSeconds <= 0) return;
        try {
            redisTemplate.opsForValue().set(
                    REVOKED_JTI_PREFIX + jti, "1", Duration.ofSeconds(remainingSeconds));
        } catch (Exception ex) {
            logger.warn("Failed to blacklist access token jti {}: {}", jti, ex.getMessage());
        }
    }

    public boolean isAccessTokenBlacklisted(String jti) {
        if (redisTemplate == null) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(REVOKED_JTI_PREFIX + jti));
        } catch (Exception ex) {
            return false;
        }
    }
}
