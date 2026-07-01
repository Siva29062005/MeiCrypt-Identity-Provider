package com.meicrypt.identity.rbac.controller;

import com.meicrypt.identity.auth.security.AuthenticatedUser;
import com.meicrypt.identity.rbac.dto.AssignRoleRequest;
import com.meicrypt.identity.rbac.dto.RoleAssignmentDTO;
import com.meicrypt.identity.rbac.service.RoleAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Role assignment administration endpoints (Module 4.3).
 *
 * A caller must:
 *   1) belong to the target organization, and
 *   2) hold {@code rbac:assignment:read} or {@code rbac:assignment:manage}
 *      depending on the operation.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments")
public class RoleAssignmentController {

    private final RoleAssignmentService roleAssignmentService;

    public RoleAssignmentController(RoleAssignmentService roleAssignmentService) {
        this.roleAssignmentService = roleAssignmentService;
    }

    @GetMapping
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('rbac:assignment:read')")
    public ResponseEntity<List<RoleAssignmentDTO>> list(@PathVariable UUID organizationId,
                                                        @PathVariable UUID membershipId) {
        return ResponseEntity.ok(roleAssignmentService.listAssignments(organizationId, membershipId));
    }

    @PostMapping
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('rbac:assignment:manage')")
    public ResponseEntity<RoleAssignmentDTO> assign(@PathVariable UUID organizationId,
                                                    @PathVariable UUID membershipId,
                                                    @Valid @RequestBody AssignRoleRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        UUID actingUserId = principal != null ? principal.userId() : null;
        RoleAssignmentDTO created = roleAssignmentService.assignRole(
                organizationId, membershipId, request.roleId(), actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('rbac:assignment:manage')")
    public ResponseEntity<Void> revoke(@PathVariable UUID organizationId,
                                       @PathVariable UUID membershipId,
                                       @PathVariable UUID roleId) {
        roleAssignmentService.revokeRole(organizationId, membershipId, roleId);
        return ResponseEntity.noContent().build();
    }
}
