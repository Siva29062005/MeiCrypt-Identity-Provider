package com.meicrypt.identity.user.controller;

import com.meicrypt.identity.user.dto.*;
import com.meicrypt.identity.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for user management operations.
 * Handles user registration, profile management, and lifecycle operations.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "APIs for user registration and profile management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Register a new user
     */
    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Register a new user account in an organization")
    public ResponseEntity<UserDTO> registerUser(@Valid @RequestBody RegisterUserRequest request) {
        UserDTO user = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    /**
     * Get user by ID
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID", description = "Retrieve user details by user ID")
    public ResponseEntity<UserDTO> getUserById(@PathVariable UUID userId) {
        UserDTO user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    /**
     * Get users by organization
     */
    @GetMapping("/organization/{organizationId}")
    @Operation(summary = "Get users by organization", description = "Retrieve all users in an organization")
    public ResponseEntity<List<UserDTO>> getUsersByOrganization(@PathVariable UUID organizationId) {
        List<UserDTO> users = userService.getUsersByOrganization(organizationId);
        return ResponseEntity.ok(users);
    }

    /**
     * Get active users by organization
     */
    @GetMapping("/organization/{organizationId}/active")
    @Operation(summary = "Get active users", description = "Retrieve all active users in an organization")
    public ResponseEntity<List<UserDTO>> getActiveUsersByOrganization(@PathVariable UUID organizationId) {
        List<UserDTO> users = userService.getActiveUsersByOrganization(organizationId);
        return ResponseEntity.ok(users);
    }

    /**
     * Update user profile
     */
    @PutMapping("/{userId}/profile")
    @Operation(summary = "Update user profile", description = "Update user profile information")
    public ResponseEntity<UserDTO> updateUserProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        UserDTO user = userService.updateUserProfile(userId, request);
        return ResponseEntity.ok(user);
    }

    /**
     * Change user password
     */
    @PostMapping("/{userId}/change-password")
    @Operation(summary = "Change password", description = "Change user password")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    /**
     * Suspend user
     */
    @PutMapping("/{userId}/suspend")
    @Operation(summary = "Suspend user", description = "Suspend user account")
    public ResponseEntity<UserDTO> suspendUser(@PathVariable UUID userId) {
        UserDTO user = userService.suspendUser(userId);
        return ResponseEntity.ok(user);
    }

    /**
     * Activate user
     */
    @PutMapping("/{userId}/activate")
    @Operation(summary = "Activate user", description = "Activate user account")
    public ResponseEntity<UserDTO> activateUser(@PathVariable UUID userId) {
        UserDTO user = userService.activateUser(userId);
        return ResponseEntity.ok(user);
    }

    /**
     * Deactivate user
     */
    @PutMapping("/{userId}/deactivate")
    @Operation(summary = "Deactivate user", description = "Deactivate user account (soft delete)")
    public ResponseEntity<UserDTO> deactivateUser(@PathVariable UUID userId) {
        UserDTO user = userService.deactivateUser(userId);
        return ResponseEntity.ok(user);
    }

    /**
     * Delete user permanently
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user", description = "Permanently delete user account")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    /**
     * Count users in organization
     */
    @GetMapping("/organization/{organizationId}/count")
    @Operation(summary = "Count users", description = "Count total users in organization")
    public ResponseEntity<Map<String, Long>> countUsers(@PathVariable UUID organizationId) {
        long count = userService.countUsersByOrganization(organizationId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Count active users in organization
     */
    @GetMapping("/organization/{organizationId}/count/active")
    @Operation(summary = "Count active users", description = "Count active users in organization")
    public ResponseEntity<Map<String, Long>> countActiveUsers(@PathVariable UUID organizationId) {
        long count = userService.countActiveUsersByOrganization(organizationId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Check if email exists
     */
    @GetMapping("/check-email")
    @Operation(summary = "Check email availability", description = "Check if email already exists in organization")
    public ResponseEntity<Map<String, Boolean>> checkEmail(
            @RequestParam String email,
            @RequestParam UUID organizationId) {
        boolean exists = userService.emailExistsInOrganization(email, organizationId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}
