package com.meicrypt.identity.rbac.exception;

/**
 * Thrown when a role cannot be resolved inside the requested organization scope.
 */
public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String message) {
        super(message);
    }
}
