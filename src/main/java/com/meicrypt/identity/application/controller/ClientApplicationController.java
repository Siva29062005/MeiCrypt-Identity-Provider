package com.meicrypt.identity.application.controller;

import com.meicrypt.identity.application.dto.ClientApplicationCredentialsDTO;
import com.meicrypt.identity.application.dto.ClientApplicationDTO;
import com.meicrypt.identity.application.dto.CreateClientApplicationRequest;
import com.meicrypt.identity.application.dto.CreateClientApplicationResponse;
import com.meicrypt.identity.application.dto.UpdateApplicationStatusRequest;
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
 * Client Application Registry endpoints (Phase 5).
 *
 * All routes are organization-scoped and guarded by SpEL expressions that
 * verify:
 * <ol>
 *   <li>the caller's active organization matches the path variable, and</li>
 *   <li>the caller carries the required {@code oauth:application:*} authority.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/applications")
public class ClientApplicationController {

    private final ClientApplicationService applicationService;

    public ClientApplicationController(ClientApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    // -----------------------------------------------------------------
    // Module 5.1 - Application Discovery
    // -----------------------------------------------------------------

    @GetMapping
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('oauth:application:read')")
    public ResponseEntity<List<ClientApplicationDTO>> list(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(applicationService.listApplications(organizationId));
    }

    @GetMapping("/{applicationId}")
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('oauth:application:read')")
    public ResponseEntity<ClientApplicationDTO> get(@PathVariable UUID organizationId,
                                                    @PathVariable UUID applicationId) {
        return ResponseEntity.ok(applicationService.getApplication(organizationId, applicationId));
    }

    // -----------------------------------------------------------------
    // Module 5.2 - Credential Issuance
    // -----------------------------------------------------------------

    @PostMapping
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('oauth:application:manage')")
    public ResponseEntity<CreateClientApplicationResponse> create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateClientApplicationRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        UUID actor = principal != null ? principal.userId() : null;
        ClientApplicationService.CreateClientApplicationResult result =
                applicationService.createApplication(organizationId, request, actor);
        CreateClientApplicationResponse body = new CreateClientApplicationResponse(
                result.application(), result.credentials());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/{applicationId}")
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('oauth:application:manage')")
    public ResponseEntity<ClientApplicationDTO> update(
            @PathVariable UUID organizationId,
            @PathVariable UUID applicationId,
            @Valid @RequestBody UpdateClientApplicationRequest request) {
        return ResponseEntity.ok(
                applicationService.updateApplication(organizationId, applicationId, request));
    }

    @PatchMapping("/{applicationId}/status")
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('oauth:application:manage')")
    public ResponseEntity<ClientApplicationDTO> changeStatus(
            @PathVariable UUID organizationId,
            @PathVariable UUID applicationId,
            @Valid @RequestBody UpdateApplicationStatusRequest request) {
        return ResponseEntity.ok(
                applicationService.changeStatus(organizationId, applicationId, request));
    }

    @DeleteMapping("/{applicationId}")
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('oauth:application:manage')")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @PathVariable UUID applicationId) {
        applicationService.deleteApplication(organizationId, applicationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{applicationId}/rotate-secret")
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('oauth:application:manage')")
    public ResponseEntity<ClientApplicationCredentialsDTO> rotateSecret(
            @PathVariable UUID organizationId,
            @PathVariable UUID applicationId) {
        return ResponseEntity.ok(
                applicationService.rotateClientSecret(organizationId, applicationId));
    }
}
