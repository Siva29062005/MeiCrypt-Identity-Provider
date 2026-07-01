package com.meicrypt.identity.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenID Connect Discovery 1.0 §3 provider configuration document.
 *
 * <p>Only the fields MeiCrypt implements are populated; the remainder are
 * omitted (Jackson drops nulls) so we never advertise unsupported behaviour.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenIdConfigurationResponse(
        @JsonProperty("issuer") String issuer,
        @JsonProperty("authorization_endpoint") String authorizationEndpoint,
        @JsonProperty("token_endpoint") String tokenEndpoint,
        @JsonProperty("introspection_endpoint") String introspectionEndpoint,
        @JsonProperty("revocation_endpoint") String revocationEndpoint,
        @JsonProperty("end_session_endpoint") String endSessionEndpoint,
        @JsonProperty("jwks_uri") String jwksUri,

        @JsonProperty("response_types_supported") List<String> responseTypesSupported,
        @JsonProperty("grant_types_supported") List<String> grantTypesSupported,
        @JsonProperty("subject_types_supported") List<String> subjectTypesSupported,
        @JsonProperty("id_token_signing_alg_values_supported") List<String> idTokenSigningAlgValuesSupported,
        @JsonProperty("token_endpoint_auth_methods_supported") List<String> tokenEndpointAuthMethodsSupported,
        @JsonProperty("code_challenge_methods_supported") List<String> codeChallengeMethodsSupported,
        @JsonProperty("scopes_supported") List<String> scopesSupported,
        @JsonProperty("claims_supported") List<String> claimsSupported,
        @JsonProperty("backchannel_logout_supported") Boolean backchannelLogoutSupported,
        @JsonProperty("backchannel_logout_session_supported") Boolean backchannelLogoutSessionSupported
) { }

