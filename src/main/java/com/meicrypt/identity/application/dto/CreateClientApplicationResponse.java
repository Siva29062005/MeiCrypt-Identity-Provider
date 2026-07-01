package com.meicrypt.identity.application.dto;

/**
 * Envelope returned by {@code POST /api/v1/organizations/{orgId}/applications}.
 *
 * Bundles the durable {@link ClientApplicationDTO} with the one-time
 * {@link ClientApplicationCredentialsDTO} that surfaces the plaintext
 * {@code client_secret}. Callers MUST cache the secret at this moment.
 */
public record CreateClientApplicationResponse(
        ClientApplicationDTO application,
        ClientApplicationCredentialsDTO credentials
) {}
