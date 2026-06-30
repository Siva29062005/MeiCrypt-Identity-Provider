package com.meicrypt.identity.organization.mapper;

import com.meicrypt.identity.organization.dto.OrganizationSettingsDTO;
import com.meicrypt.identity.organization.dto.UpdateOrganizationSettingsRequest;
import com.meicrypt.identity.organization.entity.OrganizationSettings;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for OrganizationSettings entity and DTOs.
 * Handles conversion between entity and DTO objects.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface OrganizationSettingsMapper {

    /**
     * Convert OrganizationSettings entity to OrganizationSettingsDTO.
     *
     * @param settings The organization settings entity
     * @return The organization settings DTO
     */
    OrganizationSettingsDTO toDTO(OrganizationSettings settings);

    /**
     * Update an existing OrganizationSettings entity from UpdateOrganizationSettingsRequest.
     * Only updates fields that are non-null in the request.
     *
     * @param request  The update request
     * @param settings The existing organization settings entity to update
     */
    void updateEntityFromRequest(
            UpdateOrganizationSettingsRequest request,
            @MappingTarget OrganizationSettings settings
    );
}
