package com.meicrypt.identity.application.exception;

/**
 * Thrown when a client application lookup fails inside a given organization.
 * Maps to HTTP 404 by the global exception handler.
 */
public class ClientApplicationNotFoundException extends RuntimeException {

    public ClientApplicationNotFoundException(String message) {
        super(message);
    }
}
