package com.meicrypt.identity.organization.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * OrganizationSettings entity - Customizable parameters for each organization.
 * Includes brand details, localization, password policies, and session limits.
 */
@Entity
@Table(name = "organization_settings")
@EntityListeners(AuditingEntityListener.class)
public class OrganizationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, unique = true)
    private UUID organizationId;

    // Brand customization
    @Column(name = "brand_name", length = 255)
    private String brandName;

    @Column(name = "brand_logo_url", length = 500)
    private String brandLogoUrl;

    // Localization
    @Column(name = "primary_timezone", length = 50)
    private String primaryTimezone = "UTC";

    @Column(name = "primary_language", length = 10)
    private String primaryLanguage = "en";

    // Password policies
    @Column(name = "password_min_length")
    private Integer passwordMinLength = 12;

    @Column(name = "password_require_uppercase")
    private Boolean passwordRequireUppercase = true;

    @Column(name = "password_require_lowercase")
    private Boolean passwordRequireLowercase = true;

    @Column(name = "password_require_numbers")
    private Boolean passwordRequireNumbers = true;

    @Column(name = "password_require_special_chars")
    private Boolean passwordRequireSpecialChars = true;

    // Session management
    @Column(name = "max_session_duration_minutes")
    private Integer maxSessionDurationMinutes = 480; // 8 hours default

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Default constructor required by JPA.
     */
    protected OrganizationSettings() {
        // JPA requires a no-arg constructor
    }

    /**
     * Constructor with organization ID.
     *
     * @param organizationId The organization ID these settings belong to
     */
    public OrganizationSettings(UUID organizationId) {
        this.organizationId = organizationId;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getBrandLogoUrl() {
        return brandLogoUrl;
    }

    public void setBrandLogoUrl(String brandLogoUrl) {
        this.brandLogoUrl = brandLogoUrl;
    }

    public String getPrimaryTimezone() {
        return primaryTimezone;
    }

    public void setPrimaryTimezone(String primaryTimezone) {
        this.primaryTimezone = primaryTimezone;
    }

    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    public void setPrimaryLanguage(String primaryLanguage) {
        this.primaryLanguage = primaryLanguage;
    }

    public Integer getPasswordMinLength() {
        return passwordMinLength;
    }

    public void setPasswordMinLength(Integer passwordMinLength) {
        this.passwordMinLength = passwordMinLength;
    }

    public Boolean getPasswordRequireUppercase() {
        return passwordRequireUppercase;
    }

    public void setPasswordRequireUppercase(Boolean passwordRequireUppercase) {
        this.passwordRequireUppercase = passwordRequireUppercase;
    }

    public Boolean getPasswordRequireLowercase() {
        return passwordRequireLowercase;
    }

    public void setPasswordRequireLowercase(Boolean passwordRequireLowercase) {
        this.passwordRequireLowercase = passwordRequireLowercase;
    }

    public Boolean getPasswordRequireNumbers() {
        return passwordRequireNumbers;
    }

    public void setPasswordRequireNumbers(Boolean passwordRequireNumbers) {
        this.passwordRequireNumbers = passwordRequireNumbers;
    }

    public Boolean getPasswordRequireSpecialChars() {
        return passwordRequireSpecialChars;
    }

    public void setPasswordRequireSpecialChars(Boolean passwordRequireSpecialChars) {
        this.passwordRequireSpecialChars = passwordRequireSpecialChars;
    }

    public Integer getMaxSessionDurationMinutes() {
        return maxSessionDurationMinutes;
    }

    public void setMaxSessionDurationMinutes(Integer maxSessionDurationMinutes) {
        this.maxSessionDurationMinutes = maxSessionDurationMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrganizationSettings that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "OrganizationSettings{" +
                "id=" + id +
                ", organizationId=" + organizationId +
                ", brandName='" + brandName + '\'' +
                ", primaryTimezone='" + primaryTimezone + '\'' +
                ", primaryLanguage='" + primaryLanguage + '\'' +
                ", passwordMinLength=" + passwordMinLength +
                ", maxSessionDurationMinutes=" + maxSessionDurationMinutes +
                '}';
    }
}
