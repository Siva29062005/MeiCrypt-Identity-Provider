package com.meicrypt.identity.auth.controller;

import com.meicrypt.identity.auth.dto.LoginRequest;
import com.meicrypt.identity.auth.dto.LoginResponse;
import com.meicrypt.identity.auth.dto.LogoutRequest;
import com.meicrypt.identity.auth.dto.RefreshTokenRequest;
import com.meicrypt.identity.auth.dto.TokenResponse;
import com.meicrypt.identity.auth.security.AuthenticatedUser;
import com.meicrypt.identity.auth.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, logout, and refresh token endpoints (Phase 3)")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Operation(summary = "Authenticate with email + password and obtain access/refresh tokens (or an MFA challenge if step-up is required)")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest http) {
        LoginResponse response = authenticationService.loginWithMfa(
                request, resolveClientIp(http), http.getHeader("User-Agent"));
        // 202 Accepted communicates "authenticated but pending second factor".
        if (response.requiresMfa()) {
            return ResponseEntity.accepted().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Rotate the presented refresh token and issue a fresh token pair")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                 HttpServletRequest http) {
        TokenResponse response = authenticationService.refresh(
                request.refreshToken(), resolveClientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Terminate the session bound to the presented refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody LogoutRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @Operation(summary = "Terminate all active sessions for the authenticated user")
    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAll(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthenticated"));
        }
        authenticationService.logoutAll(principal.userId());
        return ResponseEntity.ok(Map.of("message", "All sessions terminated"));
    }

    @Operation(summary = "Return the authenticated principal (for token introspection)")
    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUser> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(principal);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
