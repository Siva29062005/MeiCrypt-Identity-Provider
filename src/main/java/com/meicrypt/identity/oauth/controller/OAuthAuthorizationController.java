package com.meicrypt.identity.oauth.controller;

import com.meicrypt.identity.application.entity.ClientApplication;
import com.meicrypt.identity.auth.security.AuthenticatedUser;
import com.meicrypt.identity.oauth.dto.AuthorizationCodeResponse;
import com.meicrypt.identity.oauth.exception.OAuthException;
import com.meicrypt.identity.oauth.service.OAuthAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 /authorize endpoint (Phase 6, Module 6.1).
 *
 * <p>This endpoint MUST be called with an authenticated end-user session
 * (Phase 3 JWT). If the caller is unauthenticated, we return 401 with a
 * {@code WWW-Authenticate} hint pointing to the login endpoint - typical
 * production deployments would render an HTML login form here instead.
 *
 * <p>On success we 302-redirect to the client's {@code redirect_uri} with the
 * short-lived {@code code} + {@code state} appended as query parameters, per
 * RFC 6749 §4.1.2.
 */
@Controller
@RequestMapping("/oauth2/authorize")
@Tag(name = "OAuth2 Authorization", description = "OAuth2 authorization endpoint (Phase 6)")
public class OAuthAuthorizationController {

    private final OAuthAuthorizationService authorizationService;

    public OAuthAuthorizationController(OAuthAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "OAuth2 authorization endpoint (authorization_code flow, PKCE mandatory)")
    @GetMapping
    public ResponseEntity<?> authorize(
            @RequestParam(name = "response_type", required = false) String responseType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "nonce", required = false) String nonce,
            @RequestParam(name = "code_challenge", required = false) String codeChallenge,
            @RequestParam(name = "code_challenge_method", required = false, defaultValue = "S256")
            String codeChallengeMethod,
            HttpServletRequest request) {

        // Phase 1 validation - do NOT redirect on failure (spoofed client).
        ClientApplication client = authorizationService.resolveClientForAuthorize(clientId, redirectUri);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            // Login required. Point the caller at the login endpoint.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Bearer realm=\"meicrypt\", error=\"login_required\"")
                    .body("Login required. POST to /api/v1/auth/login and retry with the access token.");
        }

        try {
            AuthorizationCodeResponse code = authorizationService.issueAuthorizationCode(
                    client, user.userId(), user.sessionId(),
                    responseType == null ? "code" : responseType,
                    redirectUri, scope, state, nonce,
                    codeChallenge, codeChallengeMethod);
            URI target = buildSuccessRedirect(code);
            return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
        } catch (OAuthException ex) {
            if (!ex.isRedirectable()) {
                throw ex; // Handled by GlobalExceptionHandler as JSON.
            }
            URI errorTarget = buildErrorRedirect(redirectUri, ex.getError(), ex.getMessage(), state);
            return ResponseEntity.status(HttpStatus.FOUND).location(errorTarget).build();
        }
    }

    private URI buildSuccessRedirect(AuthorizationCodeResponse response) {
        var builder = UriComponentsBuilder.fromUriString(response.redirectUri())
                .queryParam("code", response.code());
        if (response.state() != null && !response.state().isBlank()) {
            builder.queryParam("state", response.state());
        }
        return builder.build(true).toUri();
    }

    private URI buildErrorRedirect(String redirectUri, String error, String description, String state) {
        var builder = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", error);
        if (description != null && !description.isBlank()) {
            builder.queryParam("error_description",
                    URLEncoder.encode(description, StandardCharsets.UTF_8));
        }
        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state);
        }
        return builder.build(true).toUri();
    }
}
