package com.meicrypt.identity.developer.controller;

import com.meicrypt.identity.application.dto.ClientApplicationCredentialsDTO;
import com.meicrypt.identity.application.dto.ClientApplicationDTO;
import com.meicrypt.identity.application.dto.CreateClientApplicationRequest;
import com.meicrypt.identity.application.dto.CreateClientApplicationResponse;
import com.meicrypt.identity.application.dto.UpdateClientApplicationRequest;
import com.meicrypt.identity.application.service.ClientApplicationService;
import com.meicrypt.identity.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Developer portal (Phase 13 - Module 13.1).
 *
 * <p>Dedicated self-service surface for the authenticated developer to manage
 * <b>their own</b> client applications: create, edit callbacks, delete, and
 * rotate secrets. The developer is always the caller — no path-level
 * {@code /organizations/{id}} scoping is needed because the principal already
 * carries an {@code organizationId} on the JWT (multi-tenant safety).
 *
 * <p>The heavy lifting is delegated to
 * {@link ClientApplicationService}; this controller only:
 * <ol>
 *   <li>projects the current principal's organization into the call, and</li>
 *   <li>guards each route with a {@code developer:*} authority so admins
 *       don't see the developer routes and developers can't hit admin
 *       routes without the corresponding organization-scoped grant.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/developer/applications")
public class DeveloperPortalController {

    private final ClientApplicationService applicationService;

    public DeveloperPortalController(ClientApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAuthority('developer:application:read')")
    public ResponseEntity<List<ClientApplicationDTO>> list(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(applicationService.listApplications(principal.organizationId()));
    }

    @GetMapping("/{applicationId}")
    @PreAuthorize("hasAuthority('developer:application:read')")
    public ResponseEntity<ClientApplicationDTO> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID applicationId) {
        return ResponseEntity.ok(
                applicationService.getApplication(principal.organizationId(), applicationId));
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAuthority('developer:application:manage')")
    public ResponseEntity<CreateClientApplicationResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateClientApplicationRequest request) {
        ClientApplicationService.CreateClientApplicationResult result =
                applicationService.createApplication(
                        principal.organizationId(), request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new CreateClientApplicationResponse(result.application(), result.credentials()));
    }

    // ------------------------------------------------------------------
    // Update (name, redirect URIs, homepage, backchannel, ...)
    // ------------------------------------------------------------------

    @PutMapping("/{applicationId}")
    @PreAuthorize("hasAuthority('developer:application:manage')")
    public ResponseEntity<ClientApplicationDTO> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID applicationId,
            @Valid @RequestBody UpdateClientApplicationRequest request) {
        return ResponseEntity.ok(applicationService.updateApplication(
                principal.organizationId(), applicationId, request));
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @DeleteMapping("/{applicationId}")
    @PreAuthorize("hasAuthority('developer:application:manage')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID applicationId) {
        applicationService.deleteApplication(principal.organizationId(), applicationId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Credential rotation (Module 13.1 rotation schedules)
    // ------------------------------------------------------------------

    @PostMapping("/{applicationId}/secret/rotate")
    @PreAuthorize("hasAuthority('developer:application:rotate_secret')")
    public ResponseEntity<ClientApplicationCredentialsDTO> rotateSecret(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID applicationId) {
        return ResponseEntity.ok(applicationService.rotateClientSecret(
                principal.organizationId(), applicationId));
    }
}
