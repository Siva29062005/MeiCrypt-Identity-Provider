package com.meicrypt.identity.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RFC 6749 §5.2 error response body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription,
        @JsonProperty("error_uri") String errorUri,
        @JsonProperty("state") String state
) {
    public static OAuthErrorResponse of(String error, String description) {
        return new OAuthErrorResponse(error, description, null, null);
    }
}
