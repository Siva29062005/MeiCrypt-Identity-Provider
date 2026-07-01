package com.meicrypt.identity.sso.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SSO session view exposed via /api/v1/sso/session and used internally by
 * the OAuth authorize flow to decide whether to skip re-authentication.
 */
public record SsoSessionDTO(
        UUID id,
        String ssoId,
        UUID userId,
        UUID organizationId,
        UUID userSessionId,
        Instant authenticatedAt,
        Instant expiresAt,
        String status,
        List<ParticipantView> participants
) {
    public record ParticipantView(
            UUID clientApplicationId,
            String clientId,
            String applicationName,
            Instant firstAuthorizedAt,
            Instant lastAuthorizedAt,
            String lastScope
    ) {}
}
