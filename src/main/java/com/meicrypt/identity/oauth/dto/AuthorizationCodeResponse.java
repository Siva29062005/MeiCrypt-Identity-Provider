package com.meicrypt.identity.oauth.dto;

/**
 * Result returned by the authorization service after a successful
 * /oauth2/authorize call. The controller assembles the redirect URL from it.
 */
public record AuthorizationCodeResponse(
        String code,
        String state,
        String redirectUri
) {}
