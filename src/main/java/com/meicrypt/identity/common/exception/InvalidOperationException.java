package com.meicrypt.identity.common.exception;

/**
 * Exception thrown when an operation is invalid in the current state or context.
 * Typically results in HTTP 400 Bad Request response.
 */
public class InvalidOperationException extends RuntimeException {

    public InvalidOperationException(String message) {
        super(message);
    }

    public InvalidOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
