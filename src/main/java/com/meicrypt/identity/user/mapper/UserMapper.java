package com.meicrypt.identity.user.mapper;

import com.meicrypt.identity.user.dto.RegisterUserRequest;
import com.meicrypt.identity.user.dto.UpdateUserProfileRequest;
import com.meicrypt.identity.user.dto.UserDTO;
import com.meicrypt.identity.user.entity.User;
import org.mapstruct.*;

/**
 * MapStruct mapper for User entity and DTOs.
 * Handles conversions between User entities and data transfer objects.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    /**
     * Convert User entity to UserDTO (excludes password hash)
     */
    UserDTO toDTO(User user);

    /**
     * Convert RegisterUserRequest to User entity
     * Note: passwordHash must be set separately after hashing
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "phoneVerified", constant = "false")
    @Mapping(target = "status", constant = "PENDING_VERIFICATION")
    @Mapping(target = "failedLoginAttempts", constant = "0")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(RegisterUserRequest request);

    /**
     * Update User entity with UpdateUserProfileRequest
     * Only updates non-null fields from the request
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "phoneVerified", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "lastLoginIp", ignore = true)
    @Mapping(target = "passwordChangedAt", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "lockedUntil", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateUserFromDTO(UpdateUserProfileRequest request, @MappingTarget User user);
}
