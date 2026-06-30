package com.meicrypt.identity.organization.entity;

/**
 * Enum defining methods available for domain verification.
 * Different organizations may prefer different verification methods.
 */
public enum DomainVerificationMethod {
    /**
     * Verification via DNS TXT record.
     * User adds a TXT record to their domain's DNS settings.
     */
    DNS_TXT,
    
    /**
     * Verification via DNS CNAME record.
     * User adds a CNAME record to their domain's DNS settings.
     */
    DNS_CNAME,
    
    /**
     * Verification via HTTP file upload.
     * User uploads a verification file to their web server.
     */
    HTTP_FILE,
    
    /**
     * Verification via email confirmation.
     * Verification link sent to domain administrator email.
     */
    EMAIL
}
