package com.meicrypt.identity.mfa.exception;

public class WebAuthnVerificationException extends MfaException {
    public WebAuthnVerificationException(String message) {
        super(message);
    }
    public WebAuthnVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
