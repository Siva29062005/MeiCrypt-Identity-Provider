package com.meicrypt.identity.application.dto;

import com.meicrypt.identity.application.entity.ApplicationStatus;
import com.meicrypt.identity.application.entity.ApplicationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public representation of a client application.
 *
 * NOTE: {@code clientSecret} is intentionally absent. The plaintext secret is
 * only ever exposed via {@link ClientApplicationCredentialsDTO} at creation and
 * rotation time.
 */
public record ClientApplicationDTO(
        UUID id,
        UUID organizationId,
        String name,
        String slug,
        String description,
        String logoUrl,
        String homepageUrl,
        ApplicationType applicationType,
        ApplicationStatus status,
        String clientId,
        List<String> grantTypes,
        List<String> scopes,
        boolean requirePkce,
        boolean requireConsent,
        int accessTokenTtlSeconds,
        int refreshTokenTtlSeconds,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        String backchannelLogoutUri,
        boolean confidential,

        boolean hasClientSecret,
        Instant clientSecretLastRotatedAt,
        Instant createdAt,
        Instant updatedAt,
        UUID createdByUserId
) {}
