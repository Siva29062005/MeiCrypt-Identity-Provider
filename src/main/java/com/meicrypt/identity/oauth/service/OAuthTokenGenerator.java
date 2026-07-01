package com.meicrypt.identity.oauth.service;

import com.meicrypt.identity.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Signs the OAuth2 access tokens / OIDC id_tokens and generates opaque
 * codes / refresh tokens for the OAuth engine.
 *
 * <p>Phase 6 shipped with HS256; Phase 7 (Module 7.2) upgrades this to RS256
 * so external relying parties can verify signatures against the public JWKS
 * document published at {@code /.well-known/jwks.json}. Every JWT carries a
 * {@code kid} header pointing at the exact key used to sign it, enabling
 * seamless key rotation.
 */
@Component
public class OAuthTokenGenerator {

    private final JwtProperties properties;
    private final OAuthSigningKeyService signingKeyService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

    public OAuthTokenGenerator(JwtProperties properties,
                               OAuthSigningKeyService signingKeyService) {
        this.properties = properties;
        this.signingKeyService = signingKeyService;
    }

    public String getIssuer() { return properties.issuer(); }

    /**
     * Issues an OAuth2 access token as a signed JWT.
     */
    public IssuedJwt issueAccessToken(UUID userId, UUID organizationId, UUID clientApplicationId,
                                      String clientId, UUID sessionId, String email, String scopes,
                                      long ttlSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        String jti = UUID.randomUUID().toString();
        var key = signingKeyService.getActiveSigningKey();
        String jwt = Jwts.builder()
                .header().keyId(key.kid()).and()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .audience().add(clientId).and()
                .claim("org_id", organizationId.toString())
                .claim("client_app_id", clientApplicationId.toString())
                .claim("sid", sessionId == null ? null : sessionId.toString())
                .claim("email", email)
                .claim("scope", scopes)
                .claim("token_type", "access_token")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .id(jti)
                .signWith(key.privateKey(), Jwts.SIG.RS256)
                .compact();
        return new IssuedJwt(jwt, jti, exp);
    }

    /**
     * Issues an OpenID Connect ID Token (Phase 7 - OIDC Core §2).
     * The signature algorithm matches the JWKS entry advertised as
     * {@code id_token_signing_alg_values_supported}.
     */
    public IssuedJwt issueIdToken(UUID userId, UUID organizationId, String clientId,
                                  String email, String nonce, long ttlSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        String jti = UUID.randomUUID().toString();
        var key = signingKeyService.getActiveSigningKey();
        var builder = Jwts.builder()
                .header().keyId(key.kid()).and()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .audience().add(clientId).and()
                .claim("org_id", organizationId.toString())
                .claim("email", email)
                .claim("email_verified", true)
                .claim("token_type", "id_token")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .id(jti);
        if (nonce != null && !nonce.isBlank()) {
            builder = builder.claim("nonce", nonce);
        }
        return new IssuedJwt(builder.signWith(key.privateKey(), Jwts.SIG.RS256).compact(), jti, exp);
    }

    /**
     * Verifies a signed OAuth/OIDC JWT using the currently-active public key.
     */
    public Claims parse(String token) throws JwtException {
        var key = signingKeyService.getActiveSigningKey();
        return Jwts.parser()
                .verifyWith(key.publicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Generates a URL-safe 32-byte opaque token (used for authorization codes
     * and refresh tokens).
     */
    public String generateOpaqueToken() {
        byte[] buf = new byte[32];
        secureRandom.nextBytes(buf);
        return urlEncoder.encodeToString(buf);
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record IssuedJwt(String jwt, String jti, Instant expiresAt) {}
}
