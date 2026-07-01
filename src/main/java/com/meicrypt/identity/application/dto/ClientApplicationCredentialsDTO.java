package com.meicrypt.identity.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One-time credential disclosure returned when a client application is created
 * or its secret is rotated.
 *
 * The {@code clientSecret} field is populated only on the initial response and
 * MUST be surfaced to the developer immediately; the plaintext is not stored
 * server-side and cannot be retrieved again. For public clients (SPA/MOBILE)
 * {@code clientSecret} is {@code null}.
 */
public record ClientApplicationCredentialsDTO(
        UUID id,
        String clientId,
        String clientSecret,
        Instant issuedAt,
        String message
) {}
