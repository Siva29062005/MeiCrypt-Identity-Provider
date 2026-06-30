package com.meicrypt.identity.user.entity;

/**
 * Enumeration of verification token types.
 */
public enum VerificationTokenType {
    /**
     * Email address verification token
     */
    EMAIL_VERIFICATION,
    
    /**
     * Phone number verification token
     */
    PHONE_VERIFICATION,
    
    /**
     * Password reset token
     */
    PASSWORD_RESET
}
