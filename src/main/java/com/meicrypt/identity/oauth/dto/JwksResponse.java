package com.meicrypt.identity.oauth.dto;

import java.util.List;

/**
 * RFC 7517 §5 JSON Web Key Set container.
 */
public record JwksResponse(List<JwkDTO> keys) { }
