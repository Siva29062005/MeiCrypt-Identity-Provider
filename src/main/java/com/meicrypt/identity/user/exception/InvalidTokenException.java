package com.meicrypt.identity.user.exception;

/**
 * Exception thrown when a verification or reset token is invalid or expired.
 */
public class InvalidTokenException extends RuntimeException {
    
    public InvalidTokenException(String message) {
        super(message);
    }
    
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
