package com.meicrypt.identity.mfa.dto;

/**
 * Client-supplied assertion returned by {@code navigator.credentials.get()}.
 * Every field is base64url-encoded per WebAuthn Level 2.
 */
public record WebAuthnAssertionPayload(
        String credentialId,
        String clientDataJsonBase64,
        String authenticatorDataBase64,
        String signatureBase64,
        String userHandleBase64
) {}
