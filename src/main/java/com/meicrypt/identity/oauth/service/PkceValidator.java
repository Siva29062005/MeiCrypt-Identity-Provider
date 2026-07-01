package com.meicrypt.identity.oauth.service;

import com.meicrypt.identity.oauth.exception.OAuthException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * RFC 7636 Proof Key for Code Exchange validator.
 *
 * MeiCrypt supports only the recommended {@code S256} transform. {@code plain}
 * is intentionally rejected as it is trivially bypassable and only kept in the
 * spec for backwards compatibility with legacy clients.
 */
@Component
public class PkceValidator {

    private static final int VERIFIER_MIN_LEN = 43;
    private static final int VERIFIER_MAX_LEN = 128;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    public void validateChallenge(String challenge, String method) {
        if (challenge == null || challenge.isBlank()) {
            throw OAuthException.invalidRequest("code_challenge is required (PKCE mandatory)");
        }
        if (method == null || method.isBlank()) {
            throw OAuthException.invalidRequest("code_challenge_method is required");
        }
        if (!"S256".equals(method)) {
            throw OAuthException.invalidRequest(
                    "Only 'S256' code_challenge_method is supported");
        }
        if (challenge.length() < VERIFIER_MIN_LEN || challenge.length() > VERIFIER_MAX_LEN) {
            throw OAuthException.invalidRequest("code_challenge has invalid length");
        }
    }

    /**
     * Confirms that the client-provided verifier maps to the stored challenge.
     * Throws {@link OAuthException} with {@code invalid_grant} on mismatch.
     */
    public void verify(String verifier, String storedChallenge, String storedMethod) {
        if (verifier == null || verifier.isBlank()) {
            throw OAuthException.invalidGrant("code_verifier is required");
        }
        if (verifier.length() < VERIFIER_MIN_LEN || verifier.length() > VERIFIER_MAX_LEN) {
            throw OAuthException.invalidGrant("code_verifier has invalid length");
        }
        if (!"S256".equalsIgnoreCase(storedMethod)) {
            throw OAuthException.invalidGrant("Unsupported code_challenge_method: " + storedMethod);
        }
        String computed = s256(verifier);
        if (!constantTimeEquals(computed, storedChallenge)) {
            throw OAuthException.invalidGrant("code_verifier does not match code_challenge");
        }
    }

    private String s256(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return URL_ENCODER.encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
