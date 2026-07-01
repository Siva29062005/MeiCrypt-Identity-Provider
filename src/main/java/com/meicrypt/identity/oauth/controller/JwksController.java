package com.meicrypt.identity.oauth.controller;

import com.meicrypt.identity.oauth.dto.JwkDTO;
import com.meicrypt.identity.oauth.dto.JwksResponse;
import com.meicrypt.identity.oauth.service.OAuthSigningKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

/**
 * Module 7.2 - JSON Web Key Set endpoint (RFC 7517).
 *
 * <p>Publishes the public halves of every ACTIVE and ROTATED signing key so
 * third-party relying parties can verify the RS256 signatures on OAuth access
 * tokens and OpenID Connect id_tokens issued by MeiCrypt.
 */
@RestController
@Tag(name = "OIDC Discovery", description = "OpenID Connect discovery + JWKS endpoints (Phase 7)")
public class JwksController {

    private final OAuthSigningKeyService signingKeyService;

    public JwksController(OAuthSigningKeyService signingKeyService) {
        this.signingKeyService = signingKeyService;
    }

    @Operation(summary = "JSON Web Key Set (RFC 7517)")
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JwksResponse> jwks() {
        List<JwkDTO> keys = signingKeyService.getPublishableKeys().stream()
                .map(this::toJwk)
                .toList();
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=300")
                .body(new JwksResponse(keys));
    }

    private JwkDTO toJwk(OAuthSigningKeyService.SigningKeyView view) {
        RSAPublicKey pub = view.publicKey();
        String n = base64Url(pub.getModulus());
        String e = base64Url(pub.getPublicExponent());
        return new JwkDTO(view.keyType(), view.keyUse(), view.algorithm(), view.kid(), n, e);
    }

    /**
     * Base64url encoding of a positive {@link BigInteger} per RFC 7518 §6.3.1.
     * Leading zero byte introduced by the two's-complement representation is
     * stripped so the resulting encoding matches JWA expectations.
     */
    private String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
