package com.meicrypt.identity.organization.mapper;

import com.meicrypt.identity.organization.dto.DomainVerificationInfoDTO;
import com.meicrypt.identity.organization.dto.OrganizationCustomDomainDTO;
import com.meicrypt.identity.organization.entity.DomainVerificationMethod;
import com.meicrypt.identity.organization.entity.OrganizationCustomDomain;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between OrganizationCustomDomain entities and DTOs.
 * Follows the zero-field-injection principle using constructor-based approach.
 */
@Component
public class OrganizationCustomDomainMapper {

    /**
     * Convert OrganizationCustomDomain entity to DTO.
     * Note: Does not include the verification token for security reasons.
     *
     * @param customDomain The custom domain entity
     * @return The custom domain DTO
     */
    public OrganizationCustomDomainDTO toDTO(OrganizationCustomDomain customDomain) {
        if (customDomain == null) {
            return null;
        }
        
        return new OrganizationCustomDomainDTO(
            customDomain.getId(),
            customDomain.getOrganizationId(),
            customDomain.getDomain(),
            customDomain.getVerificationStatus(),
            customDomain.getVerificationMethod(),
            customDomain.getVerifiedAt(),
            customDomain.getIsPrimary(),
            customDomain.getCreatedAt(),
            customDomain.getUpdatedAt()
        );
    }

    /**
     * Convert OrganizationCustomDomain entity to verification info DTO.
     * Includes the verification token and instructions.
     *
     * @param customDomain The custom domain entity
     * @return The verification info DTO
     */
    public DomainVerificationInfoDTO toVerificationInfoDTO(OrganizationCustomDomain customDomain) {
        if (customDomain == null) {
            return null;
        }
        
        String instructions = generateVerificationInstructions(
            customDomain.getVerificationMethod(),
            customDomain.getDomain(),
            customDomain.getVerificationToken()
        );
        
        return new DomainVerificationInfoDTO(
            customDomain.getDomain(),
            customDomain.getVerificationMethod(),
            customDomain.getVerificationToken(),
            instructions
        );
    }

    /**
     * Generate verification instructions based on method.
     *
     * @param method The verification method
     * @param domain The domain name
     * @param token  The verification token
     * @return Instruction text
     */
    private String generateVerificationInstructions(
            DomainVerificationMethod method, String domain, String token) {
        return switch (method) {
            case DNS_TXT -> String.format(
                "Add the following TXT record to your DNS settings:\n" +
                "Name: _meicrypt-verification.%s\n" +
                "Type: TXT\n" +
                "Value: %s", domain, token);
            case DNS_CNAME -> String.format(
                "Add the following CNAME record to your DNS settings:\n" +
                "Name: _meicrypt-verification.%s\n" +
                "Type: CNAME\n" +
                "Value: verify.meicrypt.com", domain);
            case HTTP_FILE -> String.format(
                "Upload a file to your web server:\n" +
                "URL: http://%s/.well-known/meicrypt-verification.txt\n" +
                "Content: %s", domain, token);
            case EMAIL -> String.format(
                "A verification email has been sent to admin@%s.\n" +
                "Click the link in the email to verify domain ownership.", domain);
        };
    }
}
