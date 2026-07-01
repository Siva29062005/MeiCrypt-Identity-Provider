package com.meicrypt.identity.rbac.exception;

/**
 * Thrown when an assignment attempts to link a membership to a role that
 * belongs to a different organization. Preserves multi-tenant isolation.
 */
public class CrossTenantRoleException extends RuntimeException {
    public CrossTenantRoleException(String message) {
        super(message);
    }
}
