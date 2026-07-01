package com.meicrypt.identity.auth.security;

import java.util.UUID;

/**
 * Authenticated principal placed into the SecurityContext by {@link JwtAuthenticationFilter}.
 */
public record AuthenticatedUser(
        UUID userId,
        UUID organizationId,
        UUID sessionId,
        String email,
        String jti
) {}
