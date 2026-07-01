package com.meicrypt.identity.rbac.controller;

import com.meicrypt.identity.rbac.dto.CreateRoleRequest;
import com.meicrypt.identity.rbac.dto.RoleDTO;
import com.meicrypt.identity.rbac.dto.UpdateRoleRequest;
import com.meicrypt.identity.rbac.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Organization-scoped role administration endpoints (Module 4.2).
 *
 * All routes are guarded by SpEL expressions that:
 *   1) verify the caller has the required permission, AND
 *   2) confirm the caller is authenticated inside the same organization
 *      (via {@code @rbac.sameOrganization(#organizationId)}).
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('rbac:role:read')")
    public ResponseEntity<List<RoleDTO>> list(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(roleService.listRoles(organizationId));
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('rbac:role:read')")
    public ResponseEntity<RoleDTO> get(@PathVariable UUID organizationId,
                                       @PathVariable UUID roleId) {
        return ResponseEntity.ok(roleService.getRole(organizationId, roleId));
    }

    @PostMapping
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('rbac:role:manage')")
    public ResponseEntity<RoleDTO> create(@PathVariable UUID organizationId,
                                          @Valid @RequestBody CreateRoleRequest request) {
        RoleDTO created = roleService.createRole(organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{roleId}")
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('rbac:role:manage')")
    public ResponseEntity<RoleDTO> update(@PathVariable UUID organizationId,
                                          @PathVariable UUID roleId,
                                          @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(roleService.updateRole(organizationId, roleId, request));
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('rbac:role:manage')")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @PathVariable UUID roleId) {
        roleService.deleteRole(organizationId, roleId);
        return ResponseEntity.noContent().build();
    }
}
