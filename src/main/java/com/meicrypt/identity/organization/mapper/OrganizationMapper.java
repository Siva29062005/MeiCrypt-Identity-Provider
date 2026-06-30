package com.meicrypt.identity.organization.mapper;

import com.meicrypt.identity.organization.dto.CreateOrganizationRequest;
import com.meicrypt.identity.organization.dto.OrganizationDTO;
import com.meicrypt.identity.organization.entity.Organization;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for Organization entity and DTOs.
 * Handles conversion between entity and DTO objects.
 * 
 * MapStruct will generate the implementation at compile time.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface OrganizationMapper {

    /**
     * Convert Organization entity to OrganizationDTO.
     *
     * @param organization The organization entity
     * @return The organization DTO
     */
    OrganizationDTO toDTO(Organization organization);

    /**
     * Convert CreateOrganizationRequest to Organization entity.
     * Note: Status will be set by the service layer.
     *
     * @param request The create request
     * @return The organization entity
     */
    Organization toEntity(CreateOrganizationRequest request);

    /**
     * Update an existing Organization entity from UpdateOrganizationRequest.
     * Only updates fields that are non-null in the request.
     *
     * @param request      The update request
     * @param organization The existing organization entity to update
     */
    void updateEntityFromRequest(
            com.meicrypt.identity.organization.dto.UpdateOrganizationRequest request,
            @MappingTarget Organization organization
    );
}
