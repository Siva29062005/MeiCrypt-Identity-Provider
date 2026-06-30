package com.meicrypt.identity.organization.dto;

import com.meicrypt.identity.organization.entity.DomainVerificationMethod;

/**
 * DTO containing verification instructions for a custom domain.
 * Provides the verification token and method-specific instructions.
 */
public record DomainVerificationInfoDTO(
    String domain,
    DomainVerificationMethod verificationMethod,
    String verificationToken,
    String instructions
) {
}
