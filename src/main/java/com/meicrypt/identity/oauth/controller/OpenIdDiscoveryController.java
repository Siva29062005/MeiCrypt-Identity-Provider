package com.meicrypt.identity.oauth.controller;

import com.meicrypt.identity.auth.config.JwtProperties;
import com.meicrypt.identity.oauth.dto.OpenIdConfigurationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Module 7.1 - OpenID Connect Discovery endpoint.
 *
 * <p>Publishes the provider configuration document at
 * {@code /.well-known/openid-configuration} so relying parties can auto-
 * discover MeiCrypt's OAuth/OIDC endpoints, supported grants, signing
 * algorithms, and PKCE challenge methods.
 */
@RestController
@Tag(name = "OIDC Discovery", description = "OpenID Connect discovery + JWKS endpoints (Phase 7)")
public class OpenIdDiscoveryController {

    private final JwtProperties jwtProperties;

    public OpenIdDiscoveryController(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Operation(summary = "OpenID Connect Discovery 1.0 provider configuration")
    @GetMapping(value = "/.well-known/openid-configuration",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OpenIdConfigurationResponse> configuration() {
        String issuer = trimTrailingSlash(jwtProperties.issuer());
        OpenIdConfigurationResponse body = new OpenIdConfigurationResponse(
                issuer,
                issuer + "/oauth2/authorize",
                issuer + "/oauth2/token",
                issuer + "/oauth2/introspect",
                issuer + "/oauth2/revoke",
                issuer + "/oauth2/logout",
                issuer + "/.well-known/jwks.json",
                List.of("code"),
                List.of("authorization_code", "refresh_token"),
                List.of("public"),
                List.of("RS256"),
                List.of("client_secret_basic", "client_secret_post", "none"),
                List.of("S256"),
                List.of("openid", "profile", "email"),
                List.of("sub", "iss", "aud", "exp", "iat", "nonce", "email",
                        "email_verified", "org_id", "scope", "client_app_id", "sid"),
                Boolean.TRUE,
                Boolean.TRUE);

        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=300")
                .body(body);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return value;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
