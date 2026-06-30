package com.meicrypt.identity.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating user profile.
 */
public record UpdateUserProfileRequest(
    @Size(max = 100, message = "First name must not exceed 100 characters")
    String firstName,

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    String lastName,

    @Size(max = 200, message = "Display name must not exceed 200 characters")
    String displayName,

    @Size(max = 500, message = "Profile picture URL must not exceed 500 characters")
    String profilePictureUrl,

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    String phoneNumber,

    @Size(max = 10, message = "Locale must not exceed 10 characters")
    String locale,

    @Size(max = 50, message = "Timezone must not exceed 50 characters")
    String timezone
) {
}
