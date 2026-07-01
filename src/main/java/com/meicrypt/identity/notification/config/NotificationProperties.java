package com.meicrypt.identity.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized notification worker configuration (Phase 10).
 *
 * Bound to {@code meicrypt.notifications.*}.
 */
@ConfigurationProperties(prefix = "meicrypt.notifications")
public class NotificationProperties {

    /** Batch size the outbox worker pulls per polling cycle. */
    private int batchSize = 20;

    /** Attempts before a notification is permanently marked FAILED. */
    private int maxAttempts = 5;

    /** Default locale used when no locale is provided by the caller. */
    private String defaultLocale = "en";

    /** Enable the background dispatch scheduler. */
    private boolean workerEnabled = true;

    /** Log-only "transport" toggle (no external mail/SMS gateway configured). */
    private boolean logOnlyTransport = true;

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public String getDefaultLocale() { return defaultLocale; }
    public void setDefaultLocale(String defaultLocale) { this.defaultLocale = defaultLocale; }

    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }

    public boolean isLogOnlyTransport() { return logOnlyTransport; }
    public void setLogOnlyTransport(boolean logOnlyTransport) { this.logOnlyTransport = logOnlyTransport; }
}
