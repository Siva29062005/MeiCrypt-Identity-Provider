package com.meicrypt.identity.auth.controller;

import com.meicrypt.identity.auth.dto.SessionDTO;
import com.meicrypt.identity.auth.security.AuthenticatedUser;
import com.meicrypt.identity.auth.service.AuthenticationService;
import com.meicrypt.identity.auth.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "List and revoke active user sessions (Phase 3)")
public class SessionController {

    private final SessionService sessionService;
    private final AuthenticationService authenticationService;

    public SessionController(SessionService sessionService, AuthenticationService authenticationService) {
        this.sessionService = sessionService;
        this.authenticationService = authenticationService;
    }

    @Operation(summary = "List active sessions for the authenticated user")
    @GetMapping
    public ResponseEntity<List<SessionDTO>> listMySessions(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(sessionService.listActiveSessions(principal.userId()));
    }

    @Operation(summary = "List all sessions (active + terminated) for a given user - admin use")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SessionDTO>> listUserSessions(@PathVariable UUID userId) {
        return ResponseEntity.ok(sessionService.listSessions(userId));
    }

    @Operation(summary = "Terminate a specific session by id")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, String>> terminate(@PathVariable UUID sessionId) {
        authenticationService.logoutSession(sessionId);
        return ResponseEntity.ok(Map.of("message", "Session terminated"));
    }
}
