package com.meicrypt.identity.application.exception;

/**
 * Thrown when a caller attempts to rotate / issue a client secret for a public
 * client (SPA, MOBILE). Public clients cannot store a secret securely and are
 * therefore forbidden from holding one.
 *
 * Maps to HTTP 409 by the global exception handler.
 */
public class PublicClientSecretException extends RuntimeException {

    public PublicClientSecretException(String message) {
        super(message);
    }
}
