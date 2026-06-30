package com.meicrypt.identity.organization.service;

import com.meicrypt.identity.common.exception.DuplicateResourceException;
import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import com.meicrypt.identity.organization.dto.CreateOrganizationRequest;
import com.meicrypt.identity.organization.dto.OrganizationDTO;
import com.meicrypt.identity.organization.dto.UpdateOrganizationRequest;
import com.meicrypt.identity.organization.entity.Organization;
import com.meicrypt.identity.organization.entity.OrganizationStatus;
import com.meicrypt.identity.organization.mapper.OrganizationMapper;
import com.meicrypt.identity.organization.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service layer for Organization management.
 * Implements business logic following constructor injection principle.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;
    private final OrganizationSettingsService settingsService;

    /**
     * Constructor injection - zero field injection principle.
     *
     * @param organizationRepository The organization repository
     * @param organizationMapper     The organization mapper
     * @param settingsService        The organization settings service
     */
    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMapper organizationMapper,
            OrganizationSettingsService settingsService) {
        this.organizationRepository = organizationRepository;
        this.organizationMapper = organizationMapper;
        this.settingsService = settingsService;
    }

    /**
     * Create a new organization.
     *
     * @param request The create organization request
     * @return The created organization DTO
     * @throws DuplicateResourceException if slug already exists
     */
    @Transactional
    public OrganizationDTO createOrganization(CreateOrganizationRequest request) {
        logger.info("Creating new organization with slug: {}", request.slug());

        // Check if slug already exists
        if (organizationRepository.existsBySlug(request.slug())) {
            logger.warn("Organization with slug {} already exists", request.slug());
            throw new DuplicateResourceException("Organization", "slug", request.slug());
        }

        // Convert request to entity and set default status
        Organization organization = organizationMapper.toEntity(request);
        organization.setStatus(OrganizationStatus.ACTIVE);

        // Save organization
        Organization savedOrganization = organizationRepository.save(organization);
        logger.info("Created organization with ID: {}", savedOrganization.getId());

        // Auto-create default settings for the new organization
        settingsService.createDefaultSettings(savedOrganization.getId());
        logger.info("Created default settings for organization: {}", savedOrganization.getId());

        return organizationMapper.toDTO(savedOrganization);
    }

    /**
     * Get an organization by ID.
     *
     * @param id The organization ID
     * @return The organization DTO
     * @throws ResourceNotFoundException if organization not found
     */
    public OrganizationDTO getOrganizationById(UUID id) {
        logger.debug("Fetching organization by ID: {}", id);
        
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id.toString()));

        return organizationMapper.toDTO(organization);
    }

    /**
     * Get an organization by slug.
     *
     * @param slug The organization slug
     * @return The organization DTO
     * @throws ResourceNotFoundException if organization not found
     */
    public OrganizationDTO getOrganizationBySlug(String slug) {
        logger.debug("Fetching organization by slug: {}", slug);
        
        Organization organization = organizationRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", slug));

        return organizationMapper.toDTO(organization);
    }

    /**
     * Get all organizations.
     *
     * @return List of all organizations
     */
    public List<OrganizationDTO> getAllOrganizations() {
        logger.debug("Fetching all organizations");
        
        return organizationRepository.findAll()
                .stream()
                .map(organizationMapper::toDTO)
                .toList();
    }

    /**
     * Get organizations by status.
     *
     * @param status The organization status
     * @return List of organizations with the specified status
     */
    public List<OrganizationDTO> getOrganizationsByStatus(OrganizationStatus status) {
        logger.debug("Fetching organizations by status: {}", status);
        
        return organizationRepository.findByStatus(status)
                .stream()
                .map(organizationMapper::toDTO)
                .toList();
    }

    /**
     * Search organizations by name.
     *
     * @param name The search term
     * @return List of matching organizations
     */
    public List<OrganizationDTO> searchOrganizationsByName(String name) {
        logger.debug("Searching organizations by name: {}", name);
        
        return organizationRepository.findByNameContainingIgnoreCase(name)
                .stream()
                .map(organizationMapper::toDTO)
                .toList();
    }

    /**
     * Update an existing organization.
     *
     * @param id      The organization ID
     * @param request The update request
     * @return The updated organization DTO
     * @throws ResourceNotFoundException if organization not found
     */
    @Transactional
    public OrganizationDTO updateOrganization(UUID id, UpdateOrganizationRequest request) {
        logger.info("Updating organization with ID: {}", id);

        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id.toString()));

        // Update only non-null fields
        organizationMapper.updateEntityFromRequest(request, organization);

        Organization updatedOrganization = organizationRepository.save(organization);
        logger.info("Updated organization with ID: {}", id);

        return organizationMapper.toDTO(updatedOrganization);
    }

    /**
     * Delete an organization by ID.
     *
     * @param id The organization ID
     * @throws ResourceNotFoundException if organization not found
     */
    @Transactional
    public void deleteOrganization(UUID id) {
        logger.info("Deleting organization with ID: {}", id);

        if (!organizationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Organization", id.toString());
        }

        organizationRepository.deleteById(id);
        logger.info("Deleted organization with ID: {}", id);
    }

    /**
     * Suspend an organization (change status to SUSPENDED).
     *
     * @param id The organization ID
     * @return The updated organization DTO
     * @throws ResourceNotFoundException if organization not found
     */
    @Transactional
    public OrganizationDTO suspendOrganization(UUID id) {
        logger.info("Suspending organization with ID: {}", id);

        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id.toString()));

        organization.setStatus(OrganizationStatus.SUSPENDED);
        Organization updatedOrganization = organizationRepository.save(organization);

        logger.info("Suspended organization with ID: {}", id);
        return organizationMapper.toDTO(updatedOrganization);
    }

    /**
     * Activate an organization (change status to ACTIVE).
     *
     * @param id The organization ID
     * @return The updated organization DTO
     * @throws ResourceNotFoundException if organization not found
     */
    @Transactional
    public OrganizationDTO activateOrganization(UUID id) {
        logger.info("Activating organization with ID: {}", id);

        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id.toString()));

        organization.setStatus(OrganizationStatus.ACTIVE);
        Organization updatedOrganization = organizationRepository.save(organization);

        logger.info("Activated organization with ID: {}", id);
        return organizationMapper.toDTO(updatedOrganization);
    }
}
