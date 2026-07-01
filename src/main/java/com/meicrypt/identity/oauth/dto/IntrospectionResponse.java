package com.meicrypt.identity.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RFC 7662 Token Introspection response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntrospectionResponse(
        @JsonProperty("active") boolean active,
        @JsonProperty("scope") String scope,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("username") String username,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("exp") Long exp,
        @JsonProperty("iat") Long iat,
        @JsonProperty("sub") String sub,
        @JsonProperty("aud") String aud,
        @JsonProperty("iss") String iss,
        @JsonProperty("jti") String jti
) {
    public static IntrospectionResponse inactive() {
        return new IntrospectionResponse(false, null, null, null, null, null, null, null, null, null, null);
    }
}
