package com.meicrypt.identity.mfa.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The lightweight payload returned to the client when password verification
 * requires a step-up. Carries the opaque challenge_token that must be exchanged
 * for real tokens through the verify endpoint.
 */
public record MfaChallengeDTO(
        UUID challengeId,
        String challengeToken,
        List<String> allowedFactorTypes,
        Instant expiresAt
) {}
