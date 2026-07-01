package com.meicrypt.identity.application.dto;

import com.meicrypt.identity.application.entity.ApplicationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload to register a new client application under an organization.
 *
 * If {@code slug} is omitted, the service derives one from {@code name}.
 * {@code grantTypes} and {@code scopes} default to
 * {@code [authorization_code, refresh_token]} and {@code [openid, profile, email]}
 * respectively when null/empty.
 */
public record CreateClientApplicationRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String slug,
        @Size(max = 500) String description,
        @Size(max = 500) String logoUrl,
        @Size(max = 500) String homepageUrl,

        @NotNull ApplicationType applicationType,

        List<String> redirectUris,
        List<String> postLogoutRedirectUris,

        List<String> grantTypes,
        List<String> scopes,

        Boolean requirePkce,
        Boolean requireConsent,

        @Min(60) Integer accessTokenTtlSeconds,
        @Min(60) Integer refreshTokenTtlSeconds,

        /**
         * Optional OIDC Back-Channel Logout endpoint (Phase 8, Module 8.2).
         * When present, MeiCrypt POSTs a signed {@code logout_token} here on
         * every Single Logout affecting the client's SSO participants.
         */
        @Size(max = 1000) String backchannelLogoutUri
) {}

