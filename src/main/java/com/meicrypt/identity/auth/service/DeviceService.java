package com.meicrypt.identity.auth.service;

import com.meicrypt.identity.auth.entity.UserDevice;
import com.meicrypt.identity.auth.repository.UserDeviceRepository;
import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tracks browser/client devices used by a given user.
 * Provides simple parsing of the User-Agent header for OS + browser hints.
 */
@Service
@Transactional
public class DeviceService {

    private final UserDeviceRepository deviceRepository;

    public DeviceService(UserDeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    /**
     * Return the existing device row (updated) or a freshly persisted one.
     */
    public UserDevice touchOrCreate(UUID userId, String fingerprint, String deviceName,
                                    String ipAddress, String userAgent) {
        String effectiveFingerprint = (fingerprint != null && !fingerprint.isBlank())
                ? fingerprint
                : deriveFingerprint(userAgent, ipAddress);

        return deviceRepository
                .findByUserIdAndDeviceFingerprint(userId, effectiveFingerprint)
                .map(existing -> {
                    existing.setLastIpAddress(ipAddress);
                    existing.setLastSeenAt(LocalDateTime.now());
                    if (deviceName != null) existing.setDeviceName(deviceName);
                    return deviceRepository.save(existing);
                })
                .orElseGet(() -> {
                    UserDevice fresh = new UserDevice(
                            userId,
                            effectiveFingerprint,
                            deviceName,
                            detectDeviceType(userAgent),
                            detectBrowser(userAgent),
                            detectOperatingSystem(userAgent),
                            ipAddress
                    );
                    return deviceRepository.save(fresh);
                });
    }

    @Transactional(readOnly = true)
    public List<UserDevice> listUserDevices(UUID userId) {
        return deviceRepository.findByUserIdOrderByLastSeenAtDesc(userId);
    }

    public void revokeDevice(UUID userId, UUID deviceId) {
        UserDevice device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("UserDevice", deviceId.toString()));
        if (!device.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("UserDevice", deviceId.toString());
        }
        deviceRepository.delete(device);
    }

    // -------- User-Agent heuristics (kept intentionally simple) --------

    private String deriveFingerprint(String userAgent, String ip) {
        String base = (userAgent == null ? "unknown" : userAgent) + "|" + (ip == null ? "0.0.0.0" : ip);
        return Integer.toHexString(base.hashCode());
    }

    private String detectDeviceType(String ua) {
        if (ua == null) return "UNKNOWN";
        String lower = ua.toLowerCase();
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) return "MOBILE";
        if (lower.contains("ipad") || lower.contains("tablet")) return "TABLET";
        return "DESKTOP";
    }

    private String detectBrowser(String ua) {
        if (ua == null) return "unknown";
        String lower = ua.toLowerCase();
        if (lower.contains("edg/")) return "Edge";
        if (lower.contains("chrome")) return "Chrome";
        if (lower.contains("firefox")) return "Firefox";
        if (lower.contains("safari")) return "Safari";
        if (lower.contains("curl")) return "curl";
        return "unknown";
    }

    private String detectOperatingSystem(String ua) {
        if (ua == null) return "unknown";
        String lower = ua.toLowerCase();
        if (lower.contains("windows")) return "Windows";
        if (lower.contains("mac os")) return "macOS";
        if (lower.contains("android")) return "Android";
        if (lower.contains("iphone") || lower.contains("ipad")) return "iOS";
        if (lower.contains("linux")) return "Linux";
        return "unknown";
    }
}
