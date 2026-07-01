package com.meicrypt.identity.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import com.meicrypt.identity.application.exception.ApplicationStateException;
import com.meicrypt.identity.application.exception.ClientApplicationNotFoundException;
import com.meicrypt.identity.application.exception.PublicClientSecretException;
import com.meicrypt.identity.auth.exception.AuthenticationFailedException;
import com.meicrypt.identity.auth.exception.InvalidRefreshTokenException;
import com.meicrypt.identity.auth.exception.RefreshTokenReuseException;
import com.meicrypt.identity.oauth.dto.OAuthErrorResponse;
import com.meicrypt.identity.oauth.exception.OAuthException;
import com.meicrypt.identity.rbac.exception.CrossTenantRoleException;
import com.meicrypt.identity.sso.exception.SsoSessionNotFoundException;
import com.meicrypt.identity.mfa.exception.InvalidMfaChallengeStateException;
import com.meicrypt.identity.mfa.exception.InvalidMfaCodeException;
import com.meicrypt.identity.mfa.exception.MfaChallengeNotFoundException;
import com.meicrypt.identity.mfa.exception.MfaFactorNotFoundException;
import com.meicrypt.identity.mfa.exception.WebAuthnVerificationException;
import com.meicrypt.identity.notification.exception.NotificationTemplateNotFoundException;
import org.springframework.http.ResponseEntity;



import com.meicrypt.identity.rbac.exception.ImmutableRoleException;
import com.meicrypt.identity.rbac.exception.PermissionNotFoundException;
import com.meicrypt.identity.rbac.exception.RoleNotFoundException;
import com.meicrypt.identity.user.exception.EmailAlreadyVerifiedException;
import com.meicrypt.identity.user.exception.InvalidPasswordException;
import com.meicrypt.identity.user.exception.InvalidTokenException;
import com.meicrypt.identity.user.exception.UserLockedException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler implementing RFC 7807 Problem Details for HTTP APIs.
 * Provides centralized exception handling across all REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_TYPE_BASE_URI = "https://meicrypt.com/errors/";

    /**
     * Handle validation errors from @Valid annotations on request bodies
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        
        logger.warn("Validation failed: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "validation-error"));
        problemDetail.setTitle("Validation Error");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    /**
     * Handle constraint violations from @Validated annotations
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(
            ConstraintViolationException ex,
            WebRequest request) {
        
        logger.warn("Constraint violation: {}", ex.getMessage());
        
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage
                ));

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Constraint violation in request"
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "constraint-violation"));
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    /**
     * Handle resource not found exceptions
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(
            ResourceNotFoundException ex,
            WebRequest request) {
        
        logger.warn("Resource not found: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "resource-not-found"));
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("resourceType", ex.getResourceType());
        problemDetail.setProperty("resourceId", ex.getResourceId());

        return problemDetail;
    }

    /**
     * Handle duplicate resource exceptions
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicateResource(
            DuplicateResourceException ex,
            WebRequest request) {
        
        logger.warn("Duplicate resource: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "duplicate-resource"));
        problemDetail.setTitle("Resource Already Exists");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("conflictingField", ex.getConflictingField());

        return problemDetail;
    }

    /**
     * Handle invalid operation exceptions
     */
    @ExceptionHandler(InvalidOperationException.class)
    public ProblemDetail handleInvalidOperation(
            InvalidOperationException ex,
            WebRequest request) {
        
        logger.warn("Invalid operation: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "invalid-operation"));
        problemDetail.setTitle("Invalid Operation");
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }

    /**
     * Handle authentication failures
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(
            BadCredentialsException ex,
            WebRequest request) {
        
        logger.warn("Authentication failed: Invalid credentials");

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials provided"
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "authentication-failed"));
        problemDetail.setTitle("Authentication Failed");
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }

    /**
     * Handle authorization failures
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(
            AccessDeniedException ex,
            WebRequest request) {
        
        logger.warn("Access denied: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Access denied to the requested resource"
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "access-denied"));
        problemDetail.setTitle("Access Denied");
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }

    /**
     * Handle missing static resources (favicon, images, etc.)
     * This is normal browser behavior and should not be logged as errors
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex,
            WebRequest request) {
        
        // Log at DEBUG level - browsers automatically request these
        logger.debug("Static resource not found: {}", ex.getResourcePath());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "The requested resource was not found"
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "not-found"));
        problemDetail.setTitle("Not Found");
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }

    /**
     * Handle invalid password exceptions
     */
    @ExceptionHandler(InvalidPasswordException.class)
    public ProblemDetail handleInvalidPassword(
            InvalidPasswordException ex,
            WebRequest request) {
        
        logger.warn("Invalid password: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "invalid-password"));
        problemDetail.setTitle("Invalid Password");
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }

    /**
     * Handle invalid token exceptions
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(
            InvalidTokenException ex,
            WebRequest request) {
        
        logger.warn("Invalid token: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "invalid-token"));
        problemDetail.setTitle("Invalid Token");
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }

    /**
     * Handle user locked exceptions
     */
    @ExceptionHandler(UserLockedException.class)
    public ProblemDetail handleUserLocked(
            UserLockedException ex,
            WebRequest request) {
        
        logger.warn("User account locked: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "user-locked"));
        problemDetail.setTitle("User Account Locked");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("lockedUntil", ex.getLockedUntil());

        return problemDetail;
    }

    /**
     * Handle email already verified exceptions
     */
    @ExceptionHandler(EmailAlreadyVerifiedException.class)
    public ProblemDetail handleEmailAlreadyVerified(
            EmailAlreadyVerifiedException ex,
            WebRequest request) {
        
        logger.warn("Email already verified: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "email-already-verified"));
        problemDetail.setTitle("Email Already Verified");
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }

    /**
     * Handle failed authentication (invalid credentials, suspended account, unverified email, ...).
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ProblemDetail handleAuthenticationFailed(AuthenticationFailedException ex, WebRequest request) {
        logger.warn("Authentication failed: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "authentication-failed"));
        pd.setTitle("Authentication Failed");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Handle invalid or expired refresh tokens.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex, WebRequest request) {
        logger.warn("Invalid refresh token: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "invalid-refresh-token"));
        pd.setTitle("Invalid Refresh Token");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Handle refresh token reuse (potential compromise).
     */
    @ExceptionHandler(RefreshTokenReuseException.class)
    public ProblemDetail handleRefreshTokenReuse(RefreshTokenReuseException ex, WebRequest request) {
        logger.error("Refresh token reuse detected: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "refresh-token-reuse"));
        pd.setTitle("Refresh Token Reuse Detected");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * RBAC: role lookup failed within the organization scope.
     */
    @ExceptionHandler(RoleNotFoundException.class)
    public ProblemDetail handleRoleNotFound(RoleNotFoundException ex, WebRequest request) {
        logger.warn("Role not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "role-not-found"));
        pd.setTitle("Role Not Found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * RBAC: request referenced an unknown permission code.
     */
    @ExceptionHandler(PermissionNotFoundException.class)
    public ProblemDetail handlePermissionNotFound(PermissionNotFoundException ex, WebRequest request) {
        logger.warn("Permission not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "permission-not-found"));
        pd.setTitle("Permission Not Found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * RBAC: attempted mutation of a SYSTEM role.
     */
    @ExceptionHandler(ImmutableRoleException.class)
    public ProblemDetail handleImmutableRole(ImmutableRoleException ex, WebRequest request) {
        logger.warn("Immutable role violation: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "immutable-role"));
        pd.setTitle("Immutable Role");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * RBAC: cross-tenant role assignment attempted.
     */
    @ExceptionHandler(CrossTenantRoleException.class)
    public ProblemDetail handleCrossTenantRole(CrossTenantRoleException ex, WebRequest request) {
        logger.warn("Cross-tenant role violation: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "cross-tenant-role"));
        pd.setTitle("Cross-Tenant Role Violation");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Phase 5: client application lookup failed within organization scope.
     */
    @ExceptionHandler(ClientApplicationNotFoundException.class)
    public ProblemDetail handleClientApplicationNotFound(ClientApplicationNotFoundException ex, WebRequest request) {
        logger.warn("Client application not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "client-application-not-found"));
        pd.setTitle("Client Application Not Found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Phase 5: attempt to issue/rotate a client_secret on a public client.
     */
    @ExceptionHandler(PublicClientSecretException.class)
    public ProblemDetail handlePublicClientSecret(PublicClientSecretException ex, WebRequest request) {
        logger.warn("Public client secret violation: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "public-client-secret"));
        pd.setTitle("Public Client Cannot Hold A Secret");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Phase 5: illegal lifecycle transition on a client application.
     */
    @ExceptionHandler(ApplicationStateException.class)
    public ProblemDetail handleApplicationState(ApplicationStateException ex, WebRequest request) {
        logger.warn("Illegal client application state transition: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "application-state"));
        pd.setTitle("Invalid Application State");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Phase 8: SSO session lookup failure (Module 8.1).
     */
    @ExceptionHandler(SsoSessionNotFoundException.class)
    public ProblemDetail handleSsoSessionNotFound(SsoSessionNotFoundException ex, WebRequest request) {
        logger.warn("SSO session not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "sso-session-not-found"));
        pd.setTitle("SSO Session Not Found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // ------------------------------------------------------------------
    // Phase 9 - MFA (Module 9.1 TOTP, Module 9.2 WebAuthn)
    // ------------------------------------------------------------------

    @ExceptionHandler(MfaFactorNotFoundException.class)
    public ProblemDetail handleMfaFactorNotFound(MfaFactorNotFoundException ex, WebRequest req) {
        logger.warn("MFA factor not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "mfa-factor-not-found"));
        pd.setTitle("MFA Factor Not Found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(InvalidMfaCodeException.class)
    public ProblemDetail handleInvalidMfaCode(InvalidMfaCodeException ex, WebRequest req) {
        logger.warn("Invalid MFA code: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "invalid-mfa-code"));
        pd.setTitle("Invalid MFA Code");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(MfaChallengeNotFoundException.class)
    public ProblemDetail handleMfaChallengeNotFound(MfaChallengeNotFoundException ex, WebRequest req) {
        logger.warn("MFA challenge not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "mfa-challenge-not-found"));
        pd.setTitle("MFA Challenge Not Found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(InvalidMfaChallengeStateException.class)
    public ProblemDetail handleInvalidMfaChallengeState(InvalidMfaChallengeStateException ex, WebRequest req) {
        logger.warn("Invalid MFA challenge state: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "invalid-mfa-challenge-state"));
        pd.setTitle("Invalid MFA Challenge State");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(WebAuthnVerificationException.class)
    public ProblemDetail handleWebAuthnVerification(WebAuthnVerificationException ex, WebRequest req) {
        logger.warn("WebAuthn verification failed: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "webauthn-verification-failed"));
        pd.setTitle("WebAuthn Verification Failed");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // ------------------------------------------------------------------
    // Phase 10 - Notifications
    // ------------------------------------------------------------------

    @ExceptionHandler(NotificationTemplateNotFoundException.class)
    public ProblemDetail handleNotificationTemplateNotFound(NotificationTemplateNotFoundException ex, WebRequest req) {
        logger.warn("Notification template not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create(ERROR_TYPE_BASE_URI + "notification-template-not-found"));
        pd.setTitle("Notification Template Not Found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Phase 6: OAuth2 protocol errors -> RFC 6749 §5.2 JSON error body.
     */
    @ExceptionHandler(OAuthException.class)

    public ResponseEntity<OAuthErrorResponse> handleOAuthException(OAuthException ex) {
        logger.warn("OAuth error [{}]: {}", ex.getError(), ex.getMessage());
        OAuthErrorResponse body = OAuthErrorResponse.of(ex.getError(), ex.getMessage());
        var builder = ResponseEntity.status(ex.getStatus())
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache");
        if ("invalid_client".equals(ex.getError())) {
            builder = builder.header("WWW-Authenticate", "Basic realm=\"meicrypt-oauth\"");
        }
        return builder.body(body);
    }

    /**
     * Handle all other uncaught exceptions
     */
    @ExceptionHandler(Exception.class)

    public ProblemDetail handleGlobalException(

            Exception ex,
            WebRequest request) {
        
        logger.error("Unexpected error occurred", ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later."
        );
        problemDetail.setType(URI.create(ERROR_TYPE_BASE_URI + "internal-error"));
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", Instant.now());

        return problemDetail;
    }
}
