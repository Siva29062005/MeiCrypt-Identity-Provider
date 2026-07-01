package com.meicrypt.identity.sso.controller;

import com.meicrypt.identity.application.entity.ClientApplication;
import com.meicrypt.identity.application.entity.ClientApplicationLogoutUri;
import com.meicrypt.identity.application.repository.ClientApplicationLogoutUriRepository;
import com.meicrypt.identity.application.repository.ClientApplicationRepository;
import com.meicrypt.identity.auth.security.AuthenticatedUser;
import com.meicrypt.identity.auth.service.AuthenticationService;
import com.meicrypt.identity.oauth.exception.OAuthException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.List;

/**
 * Phase 8, Module 8.2 - OpenID Connect RP-Initiated Logout endpoint.
 *
 * <p>Implements the OIDC RP-Initiated Logout 1.0 specification (§3):
 * <ol>
 *   <li>Terminates the caller's Phase-3 session (which cascades to every
 *       OAuth token via {@link AuthenticationService#logoutSession}).</li>
 *   <li>Triggers Back-Channel Logout fan-out through the SSO participant
 *       list (handled inside {@code terminateSession}).</li>
 *   <li>Redirects the user-agent to
 *       {@code post_logout_redirect_uri} (with the round-tripped
 *       {@code state}) when supplied and pre-registered against the client.
 *       Otherwise responds 204 No Content.</li>
 * </ol>
 *
 * <p>Anonymous callers are handled idempotently: nothing to terminate,
 * so we simply honour the redirect (or 204). This is required by the spec -
 * an RP calling {@code /logout} on a browser with no active session must
 * still be redirected back to complete its own logout flow.
 */
@Controller
@RequestMapping("/oauth2/logout")
@Tag(name = "OAuth2 Logout", description = "RP-Initiated / Single Logout (Phase 8, Module 8.2)")
public class OAuthLogoutController {

    private static final Logger logger = LoggerFactory.getLogger(OAuthLogoutController.class);

    private final AuthenticationService authenticationService;
    private final ClientApplicationRepository clientRepository;
    private final ClientApplicationLogoutUriRepository logoutUriRepository;

    public OAuthLogoutController(AuthenticationService authenticationService,
                                 ClientApplicationRepository clientRepository,
                                 ClientApplicationLogoutUriRepository logoutUriRepository) {
        this.authenticationService = authenticationService;
        this.clientRepository = clientRepository;
        this.logoutUriRepository = logoutUriRepository;
    }

    @Operation(summary = "OpenID Connect RP-Initiated Logout + Single Logout fan-out")
    @GetMapping
    public ResponseEntity<?> logout(
            @RequestParam(name = "id_token_hint", required = false) String idTokenHint,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
            @RequestParam(name = "state", required = false) String state) {

        // ------------------------------------------------------------------
        // 1. Resolve target client (if any) BEFORE terminating the session so
        //    an unauthenticated caller can still be redirected home.
        // ------------------------------------------------------------------
        ClientApplication client = null;
        if (clientId != null && !clientId.isBlank()) {
            client = clientRepository.findByClientId(clientId).orElse(null);
            if (client == null) {
                // Never trust an unknown redirect target. Fail closed.
                throw OAuthException.invalidRequest("Unknown client_id");
            }
        }

        // ------------------------------------------------------------------
        // 2. Validate post_logout_redirect_uri strictly against the client's
        //    pre-registered list. Spec §3: MUST NOT redirect otherwise.
        // ------------------------------------------------------------------
        URI targetRedirect = null;
        if (postLogoutRedirectUri != null && !postLogoutRedirectUri.isBlank()) {
            if (client == null) {
                throw OAuthException.invalidRequest(
                        "post_logout_redirect_uri requires client_id");
            }
            List<ClientApplicationLogoutUri> registered =
                    logoutUriRepository.findByClientApplicationId(client.getId());
            boolean allowed = registered.stream()
                    .map(ClientApplicationLogoutUri::getLogoutUri)
                    .anyMatch(postLogoutRedirectUri::equals);
            if (!allowed) {
                throw OAuthException.invalidRequest(
                        "post_logout_redirect_uri is not registered for this client");
            }
            var builder = UriComponentsBuilder.fromUriString(postLogoutRedirectUri);
            if (state != null && !state.isBlank()) {
                builder.queryParam("state", state);
            }
            targetRedirect = builder.build(true).toUri();
        }

        // ------------------------------------------------------------------
        // 3. Terminate the caller's Phase-3 session if one is present.
        //    AuthenticationService.terminateSession cascades revocation to
        //    every OAuth artifact and triggers Back-Channel Logout fan-out.
        // ------------------------------------------------------------------
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal
                && principal.sessionId() != null) {
            try {
                authenticationService.logoutSession(principal.sessionId());
                logger.info("RP-initiated logout completed for session={} client={}",
                        principal.sessionId(),
                        client == null ? "n/a" : client.getClientId());
            } catch (Exception ex) {
                // A hard failure here MUST NOT reveal internals to the RP.
                logger.warn("RP-initiated logout failed session={}: {}",
                        principal.sessionId(), ex.getMessage());
            }
        }

        // ------------------------------------------------------------------
        // 4. Redirect (302) or 204.
        // ------------------------------------------------------------------
        if (targetRedirect != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(targetRedirect).build();
        }
        return ResponseEntity.noContent().build();
    }
}
