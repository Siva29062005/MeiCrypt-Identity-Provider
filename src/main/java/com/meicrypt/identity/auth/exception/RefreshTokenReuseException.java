package com.meicrypt.identity.auth.exception;

/**
 * Thrown when a refresh token that has already been rotated is presented again.
 * This is treated as a compromise event - the entire token family is revoked.
 */
public class RefreshTokenReuseException extends RuntimeException {
    public RefreshTokenReuseException(String message) { super(message); }
}
