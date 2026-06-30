package com.meicrypt.identity.user.controller;

import com.meicrypt.identity.user.dto.ResendVerificationRequest;
import com.meicrypt.identity.user.dto.VerifyEmailRequest;
import com.meicrypt.identity.user.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for email and phone verification operations.
 */
@RestController
@RequestMapping("/api/v1/verification")
@Tag(name = "Verification", description = "APIs for email and phone verification")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * Verify email with token
     */
    @PostMapping("/verify-email")
    @Operation(summary = "Verify email", description = "Verify user email address with verification token")
    public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        verificationService.verifyEmail(request);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }

    /**
     * Resend verification email
     */
    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification", description = "Resend email verification link")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        verificationService.resendEmailVerification(request);
        return ResponseEntity.ok(Map.of("message", "Verification email sent successfully"));
    }
}
