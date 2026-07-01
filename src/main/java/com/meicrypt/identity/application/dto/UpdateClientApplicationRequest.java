package com.meicrypt.identity.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Partial update payload for an existing client application.
 *
 * All fields are optional - null means "leave unchanged". Non-null lists fully
 * replace the corresponding collection (e.g. {@code redirectUris} overrides the
 * whole set).
 *
 * The {@code applicationType} and {@code clientId} of an existing application
 * are immutable and therefore not exposed here - changing them would break any
 * deployed integrations relying on the current credentials.
 */
public record UpdateClientApplicationRequest(
        @Size(max = 150) String name,
        @Size(max = 500) String description,
        @Size(max = 500) String logoUrl,
        @Size(max = 500) String homepageUrl,

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
         * Pass an empty string to clear a previously-configured URI, or omit
         * (null) to leave the current value unchanged.
         */
        @Size(max = 1000) String backchannelLogoutUri
) {}

