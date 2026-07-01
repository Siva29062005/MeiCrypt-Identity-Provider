package com.meicrypt.identity.oauth.controller;

import com.meicrypt.identity.oauth.dto.IntrospectionResponse;
import com.meicrypt.identity.oauth.service.OAuthIntrospectionService;
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
 * RFC 7662 Token Introspection endpoint (Phase 6).
 */
@RestController
@RequestMapping("/oauth2/introspect")
@Tag(name = "OAuth2 Introspection", description = "RFC 7662 token introspection (Phase 6)")
public class OAuthIntrospectionController {

    private final OAuthIntrospectionService introspectionService;

    public OAuthIntrospectionController(OAuthIntrospectionService introspectionService) {
        this.introspectionService = introspectionService;
    }

    @Operation(summary = "Introspect an access or refresh token")
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IntrospectionResponse> introspect(
            @RequestParam("token") String token,
            @RequestParam(name = "token_type_hint", required = false) String tokenTypeHint,
            @RequestParam(name = "client_id", required = false) String formClientId,
            @RequestParam(name = "client_secret", required = false) String formClientSecret,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        String[] creds = extractCredentials(authorization, formClientId, formClientSecret);
        IntrospectionResponse response = introspectionService.introspect(token, creds[0], creds[1]);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    private String[] extractCredentials(String authorization, String formId, String formSecret) {
        if (authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String decoded = new String(
                        Base64.getDecoder().decode(authorization.substring(6).trim()),
                        StandardCharsets.UTF_8);
                int idx = decoded.indexOf(':');
                if (idx > 0) {
                    return new String[]{decoded.substring(0, idx), decoded.substring(idx + 1)};
                }
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return new String[]{formId, formSecret};
    }
}
