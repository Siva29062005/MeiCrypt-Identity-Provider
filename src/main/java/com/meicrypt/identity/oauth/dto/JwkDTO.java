package com.meicrypt.identity.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * JSON Web Key entry (RFC 7517) restricted to the fields MeiCrypt publishes
 * for RSA signature keys.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"kty", "use", "alg", "kid", "n", "e"})
public record JwkDTO(
        String kty,
        String use,
        String alg,
        String kid,
        String n,
        String e
) { }
