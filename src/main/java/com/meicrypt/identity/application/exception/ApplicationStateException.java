package com.meicrypt.identity.application.exception;

/**
 * Thrown when an operation is invalid for the application's current lifecycle
 * state (e.g. attempting to activate a REVOKED application).
 *
 * Maps to HTTP 409 by the global exception handler.
 */
public class ApplicationStateException extends RuntimeException {

    public ApplicationStateException(String message) {
        super(message);
    }
}
