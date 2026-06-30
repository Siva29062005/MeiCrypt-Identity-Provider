package com.meicrypt.identity.organization.mapper;

import com.meicrypt.identity.organization.dto.OrganizationMembershipDTO;
import com.meicrypt.identity.organization.entity.OrganizationMembership;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between OrganizationMembership entities and DTOs.
 * Follows the zero-field-injection principle using constructor-based approach.
 */
@Component
public class OrganizationMembershipMapper {

    /**
     * Convert OrganizationMembership entity to DTO.
     *
     * @param membership The membership entity
     * @return The membership DTO
     */
    public OrganizationMembershipDTO toDTO(OrganizationMembership membership) {
        if (membership == null) {
            return null;
        }
        
        return new OrganizationMembershipDTO(
            membership.getId(),
            membership.getOrganizationId(),
            membership.getUserId(),
            membership.getRole(),
            membership.getStatus(),
            membership.getJoinedAt(),
            membership.getCreatedAt(),
            membership.getUpdatedAt()
        );
    }
}
