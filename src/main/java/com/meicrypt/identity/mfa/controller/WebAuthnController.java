package com.meicrypt.identity.mfa.controller;

import com.meicrypt.identity.auth.security.AuthenticatedUser;
import com.meicrypt.identity.mfa.dto.CompleteWebAuthnRegistrationRequest;
import com.meicrypt.identity.mfa.dto.EnrollWebAuthnRequest;
import com.meicrypt.identity.mfa.dto.WebAuthnAssertionOptions;
import com.meicrypt.identity.mfa.dto.WebAuthnRegistrationOptions;
import com.meicrypt.identity.mfa.service.WebAuthnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Module 9.2 - WebAuthn / Passkey enrolment endpoints.
 */
@RestController
@RequestMapping("/api/v1/mfa/webauthn")
@Tag(name = "MFA - WebAuthn", description = "Passkey / hardware key enrolment (Phase 9, Module 9.2)")
public class WebAuthnController {

    private final WebAuthnService webAuthnService;

    public WebAuthnController(WebAuthnService webAuthnService) {
        this.webAuthnService = webAuthnService;
    }

    @Operation(summary = "Begin WebAuthn registration - returns PublicKeyCredentialCreationOptions")
    @PostMapping("/register/begin")
    public ResponseEntity<WebAuthnRegistrationOptions> beginRegistration(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody EnrollWebAuthnRequest request) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.status(201)
                .body(webAuthnService.beginRegistration(principal.userId(), request.displayName()));
    }

    @Operation(summary = "Finish WebAuthn registration by verifying the attestation payload")
    @PostMapping("/register/complete")
    public ResponseEntity<Map<String, String>> completeRegistration(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CompleteWebAuthnRegistrationRequest request) {
        if (principal == null) return ResponseEntity.status(401).build();
        webAuthnService.completeRegistration(
                principal.userId(),
                request.factorId(),
                request.credentialId(),
                request.clientDataJsonBase64(),
                request.attestationObjectBase64(),
                request.transports());
        return ResponseEntity.ok(Map.of("message", "Passkey registered"));
    }

    @Operation(summary = "Return PublicKeyCredentialRequestOptions for a step-up assertion")
    @PostMapping("/assertion/options")
    public ResponseEntity<WebAuthnAssertionOptions> assertionOptions(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(webAuthnService.beginAssertion(principal.userId()));
    }

    @Operation(summary = "Revoke an active WebAuthn factor")
    @DeleteMapping("/factors/{factorId}")
    public ResponseEntity<Map<String, String>> revoke(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID factorId) {
        if (principal == null) return ResponseEntity.status(401).build();
        webAuthnService.revokeFactor(principal.userId(), factorId);
        return ResponseEntity.ok(Map.of("message", "WebAuthn factor revoked"));
    }
}
