package com.meicrypt.identity.oauth.controller;

import com.meicrypt.identity.oauth.dto.OAuthTokenResponse;
import com.meicrypt.identity.oauth.service.OAuthTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * OAuth2 /token endpoint (Phase 6, Modules 6.1 / 6.2).
 *
 * <p>Accepts standard {@code application/x-www-form-urlencoded} bodies. Client
 * credentials may be supplied either as form parameters (public clients only
 * send client_id) or via HTTP Basic auth in the Authorization header (RFC 6749
 * §2.3.1 - preferred for confidential clients).
 */
@RestController
@RequestMapping("/oauth2/token")
@Tag(name = "OAuth2 Token", description = "OAuth2 token endpoint (Phase 6)")
public class OAuthTokenController {

    private final OAuthTokenService tokenService;

    public OAuthTokenController(OAuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Operation(summary = "OAuth2 token endpoint - authorization_code & refresh_token grants")
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OAuthTokenResponse> token(
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "client_id", required = false) String formClientId,
            @RequestParam(name = "client_secret", required = false) String formClientSecret,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "code_verifier", required = false) String codeVerifier,
            @RequestParam(name = "refresh_token", required = false) String refreshToken,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        ClientCredentials creds = extractCredentials(authorization, formClientId, formClientSecret);
        OAuthTokenResponse response = tokenService.issueToken(
                grantType, creds.clientId(), creds.clientSecret(),
                code, redirectUri, codeVerifier, refreshToken, scope);
        // RFC 6749 requires Cache-Control: no-store, Pragma: no-cache
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }

    private ClientCredentials extractCredentials(String authorization, String formId, String formSecret) {
        if (authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String decoded = new String(
                        Base64.getDecoder().decode(authorization.substring(6).trim()),
                        StandardCharsets.UTF_8);
                int idx = decoded.indexOf(':');
                if (idx > 0) {
                    return new ClientCredentials(decoded.substring(0, idx), decoded.substring(idx + 1));
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to form params.
            }
        }
        return new ClientCredentials(formId, formSecret);
    }

    private record ClientCredentials(String clientId, String clientSecret) {}
}
