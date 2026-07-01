package com.meicrypt.identity.sso.exception;

/**
 * Raised when an SSO session lookup fails (e.g. sso_id cookie no longer matches
 * an active row, or the session was terminated by Single Logout).
 */
public class SsoSessionNotFoundException extends RuntimeException {
    public SsoSessionNotFoundException(String message) {
        super(message);
    }
}
