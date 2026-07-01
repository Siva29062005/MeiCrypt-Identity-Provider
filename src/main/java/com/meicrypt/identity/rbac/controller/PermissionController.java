package com.meicrypt.identity.rbac.controller;

import com.meicrypt.identity.rbac.dto.PermissionDTO;
import com.meicrypt.identity.rbac.service.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only endpoints exposing the system permission catalog (Module 4.1).
 */
@RestController
@RequestMapping("/api/v1/rbac/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rbac:permission:read') or hasRole('USER')")
    public ResponseEntity<List<PermissionDTO>> list(@RequestParam(required = false) String domain) {
        List<PermissionDTO> permissions = (domain == null || domain.isBlank())
                ? permissionService.listAll()
                : permissionService.listByDomain(domain);
        return ResponseEntity.ok(permissions);
    }
}
