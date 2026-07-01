package com.meicrypt.identity.application.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographically-strong random generator for OAuth {@code client_id} and
 * {@code client_secret} values.
 *
 * The generator is deliberately module-local (Section 1 - Domain Boundary
 * Isolation): callers outside the application module MUST NOT reuse it - they
 * should provision credentials through {@link ClientApplicationService}.
 *
 * Format:
 *   - client_id     : 32 bytes -> 43 URL-safe chars, prefixed with {@code mip_}.
 *   - client_secret : 48 bytes -> 64 URL-safe chars, prefixed with {@code mips_}.
 *
 * Both prefixes make secret-scanning tools (GitHub, GitLab) able to detect
 * accidental credential leaks in source repositories.
 */
@Component
public class ClientCredentialGenerator {

    private static final String CLIENT_ID_PREFIX = "mip_";
    private static final String CLIENT_SECRET_PREFIX = "mips_";
    private static final int CLIENT_ID_ENTROPY_BYTES = 32;
    private static final int CLIENT_SECRET_ENTROPY_BYTES = 48;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

    public String generateClientId() {
        return CLIENT_ID_PREFIX + urlEncoder.encodeToString(randomBytes(CLIENT_ID_ENTROPY_BYTES));
    }

    public String generateClientSecret() {
        return CLIENT_SECRET_PREFIX + urlEncoder.encodeToString(randomBytes(CLIENT_SECRET_ENTROPY_BYTES));
    }

    private byte[] randomBytes(int length) {
        byte[] buffer = new byte[length];
        secureRandom.nextBytes(buffer);
        return buffer;
    }
}
