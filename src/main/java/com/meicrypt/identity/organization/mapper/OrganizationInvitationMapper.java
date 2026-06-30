package com.meicrypt.identity.organization.mapper;

import com.meicrypt.identity.organization.dto.OrganizationInvitationDTO;
import com.meicrypt.identity.organization.entity.OrganizationInvitation;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between OrganizationInvitation entities and DTOs.
 * Follows the zero-field-injection principle using constructor-based approach.
 */
@Component
public class OrganizationInvitationMapper {

    /**
     * Convert OrganizationInvitation entity to DTO.
     * Note: Does not include the invitation token for security reasons.
     *
     * @param invitation The invitation entity
     * @return The invitation DTO
     */
    public OrganizationInvitationDTO toDTO(OrganizationInvitation invitation) {
        if (invitation == null) {
            return null;
        }
        
        return new OrganizationInvitationDTO(
            invitation.getId(),
            invitation.getOrganizationId(),
            invitation.getEmail(),
            invitation.getInvitedByUserId(),
            invitation.getRole(),
            invitation.getStatus(),
            invitation.getExpiresAt(),
            invitation.getAcceptedAt(),
            invitation.getCreatedAt(),
            invitation.getUpdatedAt()
        );
    }
}
