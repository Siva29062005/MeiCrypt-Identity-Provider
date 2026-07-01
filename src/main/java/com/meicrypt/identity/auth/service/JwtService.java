package com.meicrypt.identity.auth.service;

import com.meicrypt.identity.auth.config.JwtProperties;
import com.meicrypt.identity.auth.exception.AuthenticationFailedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues and validates short-lived JWT access tokens.
 * Uses HMAC-SHA256 with a symmetric secret loaded from configuration.
 * The secret must be at least 32 bytes; a random one is generated if unset (dev only).
 */
@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    public static final String CLAIM_ORGANIZATION_ID = "org_id";
    public static final String CLAIM_SESSION_ID = "sid";
    public static final String CLAIM_EMAIL = "email";

    private final JwtProperties properties;
    private SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        String secret = properties.secret();
        byte[] keyBytes;
        if (secret == null || secret.isBlank()) {
            keyBytes = new byte[64];
            new SecureRandom().nextBytes(keyBytes);
            logger.warn("meicrypt.auth.secret is not set - generated a random signing key. " +
                    "This is acceptable only for local development.");
        } else {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                throw new IllegalStateException(
                        "meicrypt.auth.secret must be at least 32 bytes for HS256");
            }
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String issueAccessToken(UUID userId, UUID organizationId, UUID sessionId, String email) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.accessTokenTtlSeconds());
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .claim(CLAIM_ORGANIZATION_ID, organizationId.toString())
                .claim(CLAIM_SESSION_ID, sessionId.toString())
                .claim(CLAIM_EMAIL, email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException ex) {
            throw new AuthenticationFailedException("Invalid or expired access token", ex);
        }
    }

    public long getAccessTokenTtlSeconds() {
        return properties.accessTokenTtlSeconds();
    }

    /**
     * Generate an opaque refresh token (base64url-encoded 32 random bytes).
     */
    public String generateOpaqueRefreshToken() {
        byte[] buf = new byte[32];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * SHA-256 hash used for at-rest storage of opaque tokens.
     */
    public String hashOpaqueToken(String token) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
