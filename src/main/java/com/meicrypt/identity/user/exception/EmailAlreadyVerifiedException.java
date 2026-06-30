package com.meicrypt.identity.user.exception;

/**
 * Exception thrown when attempting to verify an already verified email.
 */
public class EmailAlreadyVerifiedException extends RuntimeException {
    
    public EmailAlreadyVerifiedException(String message) {
        super(message);
    }
}
