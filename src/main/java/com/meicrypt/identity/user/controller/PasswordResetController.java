package com.meicrypt.identity.user.controller;

import com.meicrypt.identity.user.dto.InitiatePasswordResetRequest;
import com.meicrypt.identity.user.dto.ResetPasswordRequest;
import com.meicrypt.identity.user.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for password reset operations.
 */
@RestController
@RequestMapping("/api/v1/password-reset")
@Tag(name = "Password Reset", description = "APIs for password reset workflow")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * Initiate password reset
     */
    @PostMapping("/initiate")
    @Operation(summary = "Initiate password reset", description = "Send password reset link to user's email")
    public ResponseEntity<Map<String, String>> initiatePasswordReset(
            @Valid @RequestBody InitiatePasswordResetRequest request) {
        passwordResetService.initiatePasswordReset(request);
        return ResponseEntity.ok(Map.of(
            "message", "If the email exists, a password reset link has been sent"
        ));
    }

    /**
     * Reset password with token
     */
    @PostMapping("/reset")
    @Operation(summary = "Reset password", description = "Reset password using reset token")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    /**
     * Validate reset token
     */
    @GetMapping("/validate-token")
    @Operation(summary = "Validate token", description = "Check if password reset token is valid")
    public ResponseEntity<Map<String, Boolean>> validateToken(@RequestParam String token) {
        boolean isValid = passwordResetService.isTokenValid(token);
        return ResponseEntity.ok(Map.of("valid", isValid));
    }
}
