package com.meicrypt.identity.rbac.exception;

/**
 * Thrown when an operation attempts to modify or delete a SYSTEM role.
 */
public class ImmutableRoleException extends RuntimeException {
    public ImmutableRoleException(String message) {
        super(message);
    }
}
