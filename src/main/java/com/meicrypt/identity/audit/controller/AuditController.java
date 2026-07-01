package com.meicrypt.identity.audit.controller;

import com.meicrypt.identity.audit.dto.AuditEventDTO;
import com.meicrypt.identity.audit.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit trail read API (Phase 11 - Module 11.1).
 *
 * Guarded by the {@code audit:log:read} authority. Writes are not exposed;
 * events are produced automatically by domain services calling
 * {@code AuditService}.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/events")
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('audit:log:read')")
    public ResponseEntity<Page<AuditEventDTO>> list(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        if (action == null && from == null && to == null) {
            return ResponseEntity.ok(auditService.listForOrganization(organizationId, page, size));
        }
        return ResponseEntity.ok(auditService.search(organizationId, action, from, to, page, size));
    }
}
