package com.meicrypt.identity.oauth.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for OAuth2 protocol errors. Wraps the RFC 6749 error code so the
 * global handler can emit an RFC-compliant JSON body (or a redirect back to
 * the client with the error in the URL for the authorization endpoint).
 */
public class OAuthException extends RuntimeException {

    private final String error;
    private final HttpStatus status;
    private final boolean redirectable;

    public OAuthException(String error, String description, HttpStatus status, boolean redirectable) {
        super(description);
        this.error = error;
        this.status = status;
        this.redirectable = redirectable;
    }

    public OAuthException(String error, String description) {
        this(error, description, HttpStatus.BAD_REQUEST, true);
    }

    public String getError() { return error; }
    public HttpStatus getStatus() { return status; }
    public boolean isRedirectable() { return redirectable; }

    // Standard RFC 6749 error codes as static factories
    public static OAuthException invalidRequest(String desc) {
        return new OAuthException("invalid_request", desc);
    }
    public static OAuthException invalidClient(String desc) {
        return new OAuthException("invalid_client", desc, HttpStatus.UNAUTHORIZED, false);
    }
    public static OAuthException invalidGrant(String desc) {
        return new OAuthException("invalid_grant", desc, HttpStatus.BAD_REQUEST, false);
    }
    public static OAuthException unauthorizedClient(String desc) {
        return new OAuthException("unauthorized_client", desc, HttpStatus.BAD_REQUEST, true);
    }
    public static OAuthException unsupportedGrantType(String desc) {
        return new OAuthException("unsupported_grant_type", desc, HttpStatus.BAD_REQUEST, false);
    }
    public static OAuthException unsupportedResponseType(String desc) {
        return new OAuthException("unsupported_response_type", desc);
    }
    public static OAuthException invalidScope(String desc) {
        return new OAuthException("invalid_scope", desc);
    }
    public static OAuthException accessDenied(String desc) {
        return new OAuthException("access_denied", desc, HttpStatus.FORBIDDEN, true);
    }
    public static OAuthException serverError(String desc) {
        return new OAuthException("server_error", desc, HttpStatus.INTERNAL_SERVER_ERROR, false);
    }
}
