package com.meicrypt.identity.rbac.service;

import com.meicrypt.identity.common.exception.DuplicateResourceException;
import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import com.meicrypt.identity.organization.entity.OrganizationMembership;
import com.meicrypt.identity.organization.repository.OrganizationMembershipRepository;
import com.meicrypt.identity.rbac.dto.RoleAssignmentDTO;
import com.meicrypt.identity.rbac.entity.MembershipRoleAssignment;
import com.meicrypt.identity.rbac.entity.Role;
import com.meicrypt.identity.rbac.exception.CrossTenantRoleException;
import com.meicrypt.identity.rbac.mapper.RbacMapper;
import com.meicrypt.identity.rbac.repository.MembershipRoleAssignmentRepository;
import com.meicrypt.identity.rbac.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Assignment Engine (Module 4.3) - binds memberships to roles.
 *
 * Two invariants are enforced at every entry point:
 * <ol>
 *   <li>The role must belong to the same organization as the membership.</li>
 *   <li>Duplicate assignments (same membership + role) are rejected.</li>
 * </ol>
 *
 * The service is also invoked internally on membership creation to attach any
 * roles marked {@code isDefault = true} for the target organization.
 */
@Service
@Transactional(readOnly = true)
public class RoleAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(RoleAssignmentService.class);

    private final MembershipRoleAssignmentRepository assignmentRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final RbacMapper rbacMapper;

    public RoleAssignmentService(MembershipRoleAssignmentRepository assignmentRepository,
                                 OrganizationMembershipRepository membershipRepository,
                                 RoleRepository roleRepository,
                                 RbacMapper rbacMapper) {
        this.assignmentRepository = assignmentRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.rbacMapper = rbacMapper;
    }

    public List<RoleAssignmentDTO> listAssignments(UUID organizationId, UUID membershipId) {
        OrganizationMembership membership = requireMembership(organizationId, membershipId);
        return assignmentRepository.findByMembershipId(membership.getId())
                .stream()
                .map(a -> rbacMapper.toDTO(a, roleRepository.findById(a.getRoleId()).orElse(null)))
                .toList();
    }

    @Transactional
    public RoleAssignmentDTO assignRole(UUID organizationId, UUID membershipId,
                                        UUID roleId, UUID assignedByUserId) {
        OrganizationMembership membership = requireMembership(organizationId, membershipId);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId.toString()));

        if (!role.getOrganizationId().equals(membership.getOrganizationId())) {
            throw new CrossTenantRoleException(
                    "Role " + roleId + " does not belong to organization " + organizationId);
        }
        if (assignmentRepository.existsByMembershipIdAndRoleId(membership.getId(), role.getId())) {
            throw new DuplicateResourceException("RoleAssignment", "role",
                    role.getId().toString());
        }

        MembershipRoleAssignment assignment = new MembershipRoleAssignment(
                membership.getId(), role.getId(), assignedByUserId);
        MembershipRoleAssignment saved = assignmentRepository.save(assignment);
        logger.info("Assigned role {} to membership {} (org {})",
                role.getId(), membership.getId(), organizationId);
        return rbacMapper.toDTO(saved, role);
    }

    @Transactional
    public void revokeRole(UUID organizationId, UUID membershipId, UUID roleId) {
        OrganizationMembership membership = requireMembership(organizationId, membershipId);
        MembershipRoleAssignment assignment = assignmentRepository
                .findByMembershipIdAndRoleId(membership.getId(), roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RoleAssignment", membership.getId() + ":" + roleId));
        assignmentRepository.delete(assignment);
        logger.info("Revoked role {} from membership {} (org {})",
                roleId, membership.getId(), organizationId);
    }

    /**
     * Attaches every default role of an organization to a freshly created
     * membership. Invoked internally by membership creation flows.
     */
    @Transactional
    public void applyDefaultRoles(UUID organizationId, UUID membershipId, UUID assignedByUserId) {
        List<Role> defaults = roleRepository.findByOrganizationIdAndDefaultRoleTrue(organizationId);
        for (Role role : defaults) {
            if (!assignmentRepository.existsByMembershipIdAndRoleId(membershipId, role.getId())) {
                assignmentRepository.save(new MembershipRoleAssignment(
                        membershipId, role.getId(), assignedByUserId));
            }
        }
    }

    private OrganizationMembership requireMembership(UUID organizationId, UUID membershipId) {
        OrganizationMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OrganizationMembership", membershipId.toString()));
        if (!membership.getOrganizationId().equals(organizationId)) {
            throw new CrossTenantRoleException(
                    "Membership " + membershipId + " does not belong to organization " + organizationId);
        }
        return membership;
    }
}
