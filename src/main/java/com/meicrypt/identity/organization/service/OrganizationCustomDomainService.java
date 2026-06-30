package com.meicrypt.identity.organization.service;

import com.meicrypt.identity.common.exception.DuplicateResourceException;
import com.meicrypt.identity.common.exception.InvalidOperationException;
import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import com.meicrypt.identity.organization.dto.CreateCustomDomainRequest;
import com.meicrypt.identity.organization.dto.DomainVerificationInfoDTO;
import com.meicrypt.identity.organization.dto.OrganizationCustomDomainDTO;
import com.meicrypt.identity.organization.entity.DomainVerificationStatus;
import com.meicrypt.identity.organization.entity.OrganizationCustomDomain;
import com.meicrypt.identity.organization.mapper.OrganizationCustomDomainMapper;
import com.meicrypt.identity.organization.repository.OrganizationCustomDomainRepository;
import com.meicrypt.identity.organization.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing organization custom domains.
 * Handles domain registration, verification, and management workflows.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationCustomDomainService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationCustomDomainService.class);
    private static final int TOKEN_BYTE_LENGTH = 32;
    
    private final OrganizationCustomDomainRepository customDomainRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationCustomDomainMapper customDomainMapper;
    private final SecureRandom secureRandom;

    public OrganizationCustomDomainService(
            OrganizationCustomDomainRepository customDomainRepository,
            OrganizationRepository organizationRepository,
            OrganizationCustomDomainMapper customDomainMapper) {
        this.customDomainRepository = customDomainRepository;
        this.organizationRepository = organizationRepository;
        this.customDomainMapper = customDomainMapper;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Create a new custom domain for an organization.
     *
     * @param request The custom domain creation request
     * @return The created custom domain DTO
     * @throws ResourceNotFoundException if organization doesn't exist
     * @throws DuplicateResourceException if domain already exists
     */
    @Transactional
    public OrganizationCustomDomainDTO createCustomDomain(CreateCustomDomainRequest request) {
        logger.info("Creating custom domain {} for organization {}", 
                   request.domain(), request.organizationId());

        // Verify organization exists
        if (!organizationRepository.existsById(request.organizationId())) {
            throw new ResourceNotFoundException("Organization", request.organizationId().toString());
        }

        // Check if domain already exists
        if (customDomainRepository.existsByDomain(request.domain())) {
            throw new DuplicateResourceException(
                "Domain already registered: " + request.domain(), "domain");
        }

        // Generate secure verification token
        String verificationToken = generateSecureToken();

        // Create new custom domain
        OrganizationCustomDomain customDomain = new OrganizationCustomDomain(
            request.organizationId(),
            request.domain(),
            verificationToken,
            request.verificationMethod()
        );

        OrganizationCustomDomain savedDomain = customDomainRepository.save(customDomain);
        logger.info("Successfully created custom domain with id: {}", savedDomain.getId());

        return customDomainMapper.toDTO(savedDomain);
    }

    /**
     * Get custom domain by ID.
     *
     * @param domainId The custom domain ID
     * @return The custom domain DTO
     * @throws ResourceNotFoundException if domain not found
     */
    public OrganizationCustomDomainDTO getCustomDomainById(UUID domainId) {
        logger.debug("Fetching custom domain with id: {}", domainId);

        OrganizationCustomDomain customDomain = customDomainRepository.findById(domainId)
            .orElseThrow(() -> new ResourceNotFoundException("CustomDomain", domainId.toString()));

        return customDomainMapper.toDTO(customDomain);
    }

    /**
     * Get all custom domains for an organization.
     *
     * @param organizationId The organization ID
     * @return List of custom domain DTOs
     */
    public List<OrganizationCustomDomainDTO> getCustomDomainsByOrganization(UUID organizationId) {
        logger.debug("Fetching custom domains for organization: {}", organizationId);

        List<OrganizationCustomDomain> domains = 
            customDomainRepository.findByOrganizationId(organizationId);

        return domains.stream()
            .map(customDomainMapper::toDTO)
            .toList();
    }

    /**
     * Get verified custom domains for an organization.
     *
     * @param organizationId The organization ID
     * @return List of verified custom domain DTOs
     */
    public List<OrganizationCustomDomainDTO> getVerifiedCustomDomainsByOrganization(
            UUID organizationId) {
        logger.debug("Fetching verified custom domains for organization: {}", organizationId);

        List<OrganizationCustomDomain> domains = 
            customDomainRepository.findByOrganizationIdAndVerificationStatus(
                organizationId, DomainVerificationStatus.VERIFIED);

        return domains.stream()
            .map(customDomainMapper::toDTO)
            .toList();
    }

    /**
     * Get verification information for a custom domain.
     *
     * @param domainId The custom domain ID
     * @return The verification info DTO
     * @throws ResourceNotFoundException if domain not found
     */
    public DomainVerificationInfoDTO getVerificationInfo(UUID domainId) {
        logger.debug("Fetching verification info for domain: {}", domainId);

        OrganizationCustomDomain customDomain = customDomainRepository.findById(domainId)
            .orElseThrow(() -> new ResourceNotFoundException("CustomDomain", domainId.toString()));

        return customDomainMapper.toVerificationInfoDTO(customDomain);
    }

    /**
     * Verify a custom domain.
     * In production, this would check DNS records or perform other verification.
     *
     * @param domainId The custom domain ID
     * @return The updated custom domain DTO
     * @throws ResourceNotFoundException if domain not found
     * @throws InvalidOperationException if domain already verified or verification failed
     */
    @Transactional
    public OrganizationCustomDomainDTO verifyCustomDomain(UUID domainId) {
        logger.info("Verifying custom domain with id: {}", domainId);

        OrganizationCustomDomain customDomain = customDomainRepository.findById(domainId)
            .orElseThrow(() -> new ResourceNotFoundException("CustomDomain", domainId.toString()));

        // Check if already verified
        if (customDomain.getVerificationStatus() == DomainVerificationStatus.VERIFIED) {
            throw new InvalidOperationException("Domain is already verified");
        }

        // TODO: In production, implement actual verification logic based on method
        // For now, we'll mark it as verified
        boolean verificationSuccessful = performDomainVerification(customDomain);

        if (verificationSuccessful) {
            customDomain.setVerificationStatus(DomainVerificationStatus.VERIFIED);
            customDomain.setVerifiedAt(Instant.now());
            logger.info("Successfully verified custom domain: {}", customDomain.getDomain());
        } else {
            customDomain.setVerificationStatus(DomainVerificationStatus.FAILED);
            logger.warn("Failed to verify custom domain: {}", customDomain.getDomain());
            throw new InvalidOperationException("Domain verification failed");
        }

        OrganizationCustomDomain updatedDomain = customDomainRepository.save(customDomain);
        return customDomainMapper.toDTO(updatedDomain);
    }

    /**
     * Set a custom domain as primary for the organization.
     *
     * @param domainId The custom domain ID
     * @return The updated custom domain DTO
     * @throws ResourceNotFoundException if domain not found
     * @throws InvalidOperationException if domain not verified
     */
    @Transactional
    public OrganizationCustomDomainDTO setPrimaryDomain(UUID domainId) {
        logger.info("Setting custom domain {} as primary", domainId);

        OrganizationCustomDomain customDomain = customDomainRepository.findById(domainId)
            .orElseThrow(() -> new ResourceNotFoundException("CustomDomain", domainId.toString()));

        // Only verified domains can be set as primary
        if (!customDomain.isVerified()) {
            throw new InvalidOperationException(
                "Only verified domains can be set as primary");
        }

        // Remove primary flag from current primary domain
        customDomainRepository.findByOrganizationIdAndIsPrimary(
            customDomain.getOrganizationId(), true)
            .ifPresent(currentPrimary -> {
                currentPrimary.setIsPrimary(false);
                customDomainRepository.save(currentPrimary);
            });

        // Set this domain as primary
        customDomain.setIsPrimary(true);
        OrganizationCustomDomain updatedDomain = customDomainRepository.save(customDomain);

        logger.info("Successfully set domain {} as primary", customDomain.getDomain());
        return customDomainMapper.toDTO(updatedDomain);
    }

    /**
     * Delete a custom domain.
     *
     * @param domainId The custom domain ID
     * @throws ResourceNotFoundException if domain not found
     * @throws InvalidOperationException if domain is primary
     */
    @Transactional
    public void deleteCustomDomain(UUID domainId) {
        logger.info("Deleting custom domain with id: {}", domainId);

        OrganizationCustomDomain customDomain = customDomainRepository.findById(domainId)
            .orElseThrow(() -> new ResourceNotFoundException("CustomDomain", domainId.toString()));

        // Cannot delete primary domain
        if (customDomain.getIsPrimary()) {
            throw new InvalidOperationException(
                "Cannot delete primary domain. Set another domain as primary first.");
        }

        customDomainRepository.delete(customDomain);
        logger.info("Successfully deleted custom domain: {}", customDomain.getDomain());
    }

    /**
     * Perform domain verification based on verification method.
     * This is a placeholder for actual verification logic.
     *
     * @param customDomain The custom domain to verify
     * @return true if verification successful
     */
    private boolean performDomainVerification(OrganizationCustomDomain customDomain) {
        // TODO: Implement actual verification logic
        // For DNS_TXT: Query DNS TXT records
        // For DNS_CNAME: Query DNS CNAME records
        // For HTTP_FILE: Fetch HTTP file
        // For EMAIL: Check if email was confirmed
        
        logger.debug("Performing {} verification for domain: {}", 
                    customDomain.getVerificationMethod(), customDomain.getDomain());
        
        // For now, return true to allow testing
        // In production, this would perform actual verification checks
        return true;
    }

    /**
     * Generate a secure random token for domain verification.
     *
     * @return Base64-encoded secure random token
     */
    private String generateSecureToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
