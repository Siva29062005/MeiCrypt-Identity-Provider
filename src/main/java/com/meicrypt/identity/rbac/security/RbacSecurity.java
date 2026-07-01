package com.meicrypt.identity.rbac.security;

import com.meicrypt.identity.auth.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SpEL-friendly authorization helper (Module 4.4).
 *
 * Exposed as the bean name {@code "rbac"} so controller methods can write:
 *
 * <pre>{@code
 *   @PreAuthorize("@rbac.hasPermission(#organizationId, 'rbac:role:manage')")
 * }</pre>
 *
 * The helper enforces multi-tenant scoping: a permission is only considered
 * granted if the currently authenticated principal is bound to the exact
 * organization referenced in the URL path.
 */
@Component("rbac")
public class RbacSecurity {

    /**
     * True when the principal is authenticated inside {@code organizationId}
     * and carries the given permission code as a granted authority.
     */
    public boolean hasPermission(UUID organizationId, String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser user)) {
            return false;
        }
        if (organizationId != null && !organizationId.equals(user.organizationId())) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (permissionCode.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the principal's active organization matches the given id.
     */
    public boolean sameOrganization(UUID organizationId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return false;
        }
        return organizationId != null && organizationId.equals(user.organizationId());
    }
}
