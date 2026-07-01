package com.meicrypt.identity.rbac.exception;

/**
 * Thrown when a referenced permission code does not exist in the system catalog.
 */
public class PermissionNotFoundException extends RuntimeException {
    public PermissionNotFoundException(String message) {
        super(message);
    }
}
