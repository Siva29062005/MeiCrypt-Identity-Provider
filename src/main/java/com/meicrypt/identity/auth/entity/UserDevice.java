package com.meicrypt.identity.auth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Client device / browser fingerprint tracked per user.
 * Enables users to audit and revoke access from specific devices.
 */
@Entity
@Table(name = "user_devices",
       uniqueConstraints = @UniqueConstraint(
               name = "unique_user_device_fingerprint",
               columnNames = {"user_id", "device_fingerprint"}),
       indexes = {
               @Index(name = "idx_user_devices_user_id", columnList = "user_id"),
               @Index(name = "idx_user_devices_last_seen_at", columnList = "last_seen_at")
       })
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_fingerprint", nullable = false, length = 255)
    private String deviceFingerprint;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "operating_system", length = 100)
    private String operatingSystem;

    @Column(name = "last_ip_address", length = 45)
    private String lastIpAddress;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "trusted", nullable = false)
    private Boolean trusted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public UserDevice() {
    }

    public UserDevice(UUID userId, String deviceFingerprint,
                      String deviceName, String deviceType,
                      String browser, String operatingSystem,
                      String lastIpAddress) {
        this.userId = userId;
        this.deviceFingerprint = deviceFingerprint;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.browser = browser;
        this.operatingSystem = operatingSystem;
        this.lastIpAddress = lastIpAddress;
        this.lastSeenAt = LocalDateTime.now();
        this.trusted = false;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }
    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
    public String getLastIpAddress() { return lastIpAddress; }
    public void setLastIpAddress(String lastIpAddress) { this.lastIpAddress = lastIpAddress; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Boolean getTrusted() { return trusted; }
    public void setTrusted(Boolean trusted) { this.trusted = trusted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
