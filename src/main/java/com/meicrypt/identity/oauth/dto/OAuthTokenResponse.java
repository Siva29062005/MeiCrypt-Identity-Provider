package com.meicrypt.identity.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RFC 6749 §5.1 successful token response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("scope") String scope,
        @JsonProperty("id_token") String idToken
) {
    public static OAuthTokenResponse bearer(String accessToken, long expiresIn,
                                            String refreshToken, String scope, String idToken) {
        return new OAuthTokenResponse(accessToken, "Bearer", expiresIn, refreshToken, scope, idToken);
    }
}
