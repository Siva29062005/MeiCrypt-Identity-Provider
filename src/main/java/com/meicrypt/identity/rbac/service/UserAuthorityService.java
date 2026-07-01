package com.meicrypt.identity.rbac.service;

import com.meicrypt.identity.organization.entity.MembershipStatus;
import com.meicrypt.identity.organization.entity.OrganizationMembership;
import com.meicrypt.identity.organization.repository.OrganizationMembershipRepository;
import com.meicrypt.identity.rbac.entity.Permission;
import com.meicrypt.identity.rbac.entity.Role;
import com.meicrypt.identity.rbac.repository.RoleRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Computes the effective {@link GrantedAuthority} set for an authenticated
 * principal inside a specific organization context (Module 4.4 support).
 *
 * <p>Two flavors of authority are emitted:
 * <ul>
 *   <li>{@code ROLE_<slug_upper>} - one per assigned role (compatible with
 *       Spring's {@code hasRole(...)} expressions).</li>
 *   <li>Raw permission codes such as {@code identity:user:read} - used by
 *       {@code hasAuthority(...)} expressions.</li>
 * </ul>
 *
 * The lookup path is:
 *   user + organization  ->  membership (must be ACTIVE)
 *                        ->  assigned roles (with permissions fetched eagerly)
 *                        ->  union of permission codes.
 */
@Service
@Transactional(readOnly = true)
public class UserAuthorityService {

    private final OrganizationMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;

    public UserAuthorityService(OrganizationMembershipRepository membershipRepository,
                                RoleRepository roleRepository) {
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
    }

    public Set<GrantedAuthority> loadAuthorities(UUID userId, UUID organizationId) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        // Baseline authority carried by every authenticated principal
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        if (userId == null || organizationId == null) {
            return authorities;
        }

        OrganizationMembership membership = membershipRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElse(null);
        if (membership == null || membership.getStatus() != MembershipStatus.ACTIVE) {
            return authorities;
        }

        List<Role> roles = roleRepository.findRolesWithPermissionsByMembershipId(membership.getId());
        Set<String> emittedPermissions = new HashSet<>();
        for (Role role : roles) {
            authorities.add(new SimpleGrantedAuthority(
                    "ROLE_" + role.getSlug().toUpperCase().replace('-', '_')));
            if (role.getPermissions() == null) {
                continue;
            }
            for (Permission permission : role.getPermissions()) {
                if (emittedPermissions.add(permission.getCode())) {
                    authorities.add(new SimpleGrantedAuthority(permission.getCode()));
                }
            }
        }
        return authorities;
    }
}
