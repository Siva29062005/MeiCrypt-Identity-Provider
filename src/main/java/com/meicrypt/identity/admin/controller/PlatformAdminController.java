package com.meicrypt.identity.admin.controller;

import com.meicrypt.identity.admin.dto.PlatformOrganizationSummaryDTO;
import com.meicrypt.identity.admin.dto.PlatformStatsDTO;
import com.meicrypt.identity.admin.dto.UpdateOrganizationStatusRequest;
import com.meicrypt.identity.admin.service.PlatformAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Platform-admin console endpoints (Phase 12 - Module 12.1).
 *
 * <p>Every route requires the {@code platform:*} authority, which is only
 * granted to members of the synthetic {@code meicrypt-platform} organization
 * carrying the {@code platform-admin} role (seeded via Flyway V13).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;

    public PlatformAdminController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('platform:organization:read')")
    public ResponseEntity<PlatformStatsDTO> stats() {
        return ResponseEntity.ok(platformAdminService.getPlatformStats());
    }

    @GetMapping("/organizations")
    @PreAuthorize("hasAuthority('platform:organization:read')")
    public ResponseEntity<List<PlatformOrganizationSummaryDTO>> listOrganizations() {
        return ResponseEntity.ok(platformAdminService.listOrganizations());
    }

    @PutMapping("/organizations/{organizationId}/status")
    @PreAuthorize("hasAuthority('platform:organization:manage')")
    public ResponseEntity<PlatformOrganizationSummaryDTO> updateOrganizationStatus(
            @PathVariable UUID organizationId,
            @Valid @RequestBody UpdateOrganizationStatusRequest request) {
        return ResponseEntity.ok(platformAdminService.updateOrganizationStatus(organizationId, request));
    }
}
