package com.meicrypt.identity.organization.service;

import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import com.meicrypt.identity.organization.dto.OrganizationSettingsDTO;
import com.meicrypt.identity.organization.dto.UpdateOrganizationSettingsRequest;
import com.meicrypt.identity.organization.entity.OrganizationSettings;
import com.meicrypt.identity.organization.mapper.OrganizationSettingsMapper;
import com.meicrypt.identity.organization.repository.OrganizationRepository;
import com.meicrypt.identity.organization.repository.OrganizationSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service layer for OrganizationSettings management.
 * Implements business logic for organization settings.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationSettingsService.class);

    private final OrganizationSettingsRepository settingsRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationSettingsMapper settingsMapper;

    /**
     * Constructor injection - zero field injection principle.
     *
     * @param settingsRepository    The organization settings repository
     * @param organizationRepository The organization repository
     * @param settingsMapper         The organization settings mapper
     */
    public OrganizationSettingsService(
            OrganizationSettingsRepository settingsRepository,
            OrganizationRepository organizationRepository,
            OrganizationSettingsMapper settingsMapper) {
        this.settingsRepository = settingsRepository;
        this.organizationRepository = organizationRepository;
        this.settingsMapper = settingsMapper;
    }

    /**
     * Get organization settings by organization ID.
     * If settings don't exist, creates default settings automatically.
     *
     * @param organizationId The organization ID
     * @return The organization settings DTO
     * @throws ResourceNotFoundException if organization doesn't exist
     */
    @Transactional
    public OrganizationSettingsDTO getOrganizationSettings(UUID organizationId) {
        logger.debug("Fetching settings for organization: {}", organizationId);

        // Verify organization exists
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", organizationId.toString());
        }

        // Get or create settings
        OrganizationSettings settings = settingsRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    logger.info("Creating default settings for organization: {}", organizationId);
                    OrganizationSettings newSettings = new OrganizationSettings(organizationId);
                    return settingsRepository.save(newSettings);
                });

        return settingsMapper.toDTO(settings);
    }

    /**
     * Update organization settings.
     *
     * @param organizationId The organization ID
     * @param request        The update request
     * @return The updated organization settings DTO
     * @throws ResourceNotFoundException if organization doesn't exist
     */
    @Transactional
    public OrganizationSettingsDTO updateOrganizationSettings(
            UUID organizationId,
            UpdateOrganizationSettingsRequest request) {
        
        logger.info("Updating settings for organization: {}", organizationId);

        // Verify organization exists
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", organizationId.toString());
        }

        // Get or create settings
        OrganizationSettings settings = settingsRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    logger.info("Creating settings for organization: {}", organizationId);
                    return new OrganizationSettings(organizationId);
                });

        // Update with request data
        settingsMapper.updateEntityFromRequest(request, settings);

        OrganizationSettings updatedSettings = settingsRepository.save(settings);
        logger.info("Updated settings for organization: {}", organizationId);

        return settingsMapper.toDTO(updatedSettings);
    }

    /**
     * Reset organization settings to defaults.
     *
     * @param organizationId The organization ID
     * @return The reset organization settings DTO
     * @throws ResourceNotFoundException if organization doesn't exist
     */
    @Transactional
    public OrganizationSettingsDTO resetOrganizationSettings(UUID organizationId) {
        logger.info("Resetting settings for organization: {}", organizationId);

        // Verify organization exists
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", organizationId.toString());
        }

        // Delete existing settings if present
        settingsRepository.deleteByOrganizationId(organizationId);

        // Create new default settings
        OrganizationSettings newSettings = new OrganizationSettings(organizationId);
        OrganizationSettings savedSettings = settingsRepository.save(newSettings);

        logger.info("Reset settings for organization: {}", organizationId);
        return settingsMapper.toDTO(savedSettings);
    }

    /**
     * Create default settings for a new organization.
     * Called internally when a new organization is created.
     *
     * @param organizationId The organization ID
     * @return The created organization settings DTO
     */
    @Transactional
    public OrganizationSettingsDTO createDefaultSettings(UUID organizationId) {
        logger.info("Creating default settings for organization: {}", organizationId);

        OrganizationSettings settings = new OrganizationSettings(organizationId);
        OrganizationSettings savedSettings = settingsRepository.save(settings);

        return settingsMapper.toDTO(savedSettings);
    }
}
