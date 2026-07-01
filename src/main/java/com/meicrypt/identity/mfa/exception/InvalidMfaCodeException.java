package com.meicrypt.identity.mfa.exception;

public class InvalidMfaCodeException extends MfaException {
    public InvalidMfaCodeException(String message) {
        super(message);
    }
}
