package com.meicrypt.identity.mfa.exception;

/**
 * Base exception for Phase 9 MFA errors.
 */
public abstract class MfaException extends RuntimeException {
    protected MfaException(String message) {
        super(message);
    }
    protected MfaException(String message, Throwable cause) {
        super(message, cause);
    }
}
