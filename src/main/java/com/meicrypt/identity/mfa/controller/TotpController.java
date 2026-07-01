package com.meicrypt.identity.mfa.controller;

import com.meicrypt.identity.auth.security.AuthenticatedUser;
import com.meicrypt.identity.mfa.dto.EnrollTotpRequest;
import com.meicrypt.identity.mfa.dto.TotpEnrollmentResponse;
import com.meicrypt.identity.mfa.dto.VerifyTotpEnrollmentRequest;
import com.meicrypt.identity.mfa.service.TotpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Module 9.1 - TOTP authenticator-app enrolment endpoints.
 */
@RestController
@RequestMapping("/api/v1/mfa/totp")
@Tag(name = "MFA - TOTP", description = "Authenticator App enrolment (Phase 9, Module 9.1)")
public class TotpController {

    private final TotpService totpService;

    public TotpController(TotpService totpService) {
        this.totpService = totpService;
    }

    @Operation(summary = "Begin TOTP enrolment - returns the shared secret, otpauth URI, and QR code")
    @PostMapping("/enroll")
    public ResponseEntity<TotpEnrollmentResponse> enroll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody EnrollTotpRequest request) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.status(201)
                .body(totpService.enrollTotp(principal.userId(), request.displayName()));
    }

    @Operation(summary = "Confirm the TOTP enrolment by presenting the first authenticator code")
    @PostMapping("/factors/{factorId}/verify")
    public ResponseEntity<Map<String, String>> verifyEnrollment(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID factorId,
            @Valid @RequestBody VerifyTotpEnrollmentRequest request) {
        if (principal == null) return ResponseEntity.status(401).build();
        totpService.verifyTotpEnrollment(principal.userId(), factorId, request.code());
        return ResponseEntity.ok(Map.of("message", "TOTP factor activated"));
    }

    @Operation(summary = "Revoke an active TOTP factor")
    @DeleteMapping("/factors/{factorId}")
    public ResponseEntity<Map<String, String>> revoke(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID factorId) {
        if (principal == null) return ResponseEntity.status(401).build();
        totpService.revokeFactor(principal.userId(), factorId);
        return ResponseEntity.ok(Map.of("message", "TOTP factor revoked"));
    }
}
