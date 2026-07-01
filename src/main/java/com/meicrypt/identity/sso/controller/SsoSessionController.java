package com.meicrypt.identity.sso.controller;

import com.meicrypt.identity.auth.security.AuthenticatedUser;
import com.meicrypt.identity.sso.dto.SsoSessionDTO;
import com.meicrypt.identity.sso.service.SsoSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 8, Module 8.1 - SSO session introspection endpoint.
 *
 * <p>Returns the current caller's SSO session (if any) along with the list of
 * OAuth client applications that have been authorized through it. Used by
 * admin dashboards and developer portals to visualize the "signed in
 * everywhere" state and by clients performing silent authentication checks.
 */
@RestController
@RequestMapping("/api/v1/sso")
@Tag(name = "SSO Federation", description = "Cross-application SSO session state (Phase 8, Module 8.1)")
public class SsoSessionController {

    private final SsoSessionService ssoSessionService;

    public SsoSessionController(SsoSessionService ssoSessionService) {
        this.ssoSessionService = ssoSessionService;
    }

    @Operation(summary = "Get the caller's SSO session with participating applications")
    @GetMapping("/session")
    public ResponseEntity<SsoSessionDTO> currentSession(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null || principal.sessionId() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(ssoSessionService.describe(principal.sessionId()));
    }
}
