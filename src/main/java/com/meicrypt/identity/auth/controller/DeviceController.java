package com.meicrypt.identity.auth.controller;

import com.meicrypt.identity.auth.dto.DeviceDTO;
import com.meicrypt.identity.auth.mapper.SessionMapper;
import com.meicrypt.identity.auth.security.AuthenticatedUser;
import com.meicrypt.identity.auth.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Devices", description = "Manage authenticated devices (Phase 3.5)")
public class DeviceController {

    private final DeviceService deviceService;
    private final SessionMapper sessionMapper;

    public DeviceController(DeviceService deviceService, SessionMapper sessionMapper) {
        this.deviceService = deviceService;
        this.sessionMapper = sessionMapper;
    }

    @Operation(summary = "List all devices the authenticated user has signed in from")
    @GetMapping
    public ResponseEntity<List<DeviceDTO>> myDevices(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        List<DeviceDTO> devices = deviceService.listUserDevices(principal.userId()).stream()
                .map(sessionMapper::toDTO)
                .toList();
        return ResponseEntity.ok(devices);
    }

    @Operation(summary = "Admin: list devices for a specific user")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<DeviceDTO>> userDevices(@PathVariable UUID userId) {
        List<DeviceDTO> devices = deviceService.listUserDevices(userId).stream()
                .map(sessionMapper::toDTO)
                .toList();
        return ResponseEntity.ok(devices);
    }

    @Operation(summary = "Revoke a device from the authenticated user's account")
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Map<String, String>> revoke(
            @PathVariable UUID deviceId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        deviceService.revokeDevice(principal.userId(), deviceId);
        return ResponseEntity.ok(Map.of("message", "Device revoked"));
    }
}
