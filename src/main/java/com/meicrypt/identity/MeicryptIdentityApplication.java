package com.meicrypt.identity;

import com.meicrypt.identity.mfa.config.MfaProperties;
import com.meicrypt.identity.notification.config.NotificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for MeiCrypt Identity Platform.
 * 
 * This is an enterprise multi-tenant identity and access management platform
 * providing OAuth2/OIDC, RBAC, MFA, and SSO capabilities.
 * 
 * Key Features:
 * - Multi-tenant organization management
 * - User identity lifecycle management
 * - OAuth2 and OpenID Connect compliance
 * - Role-based access control (RBAC)
 * - Multi-factor authentication (MFA)
 * - Single Sign-On (SSO) federation
 * - Comprehensive audit logging
 * 
 * Architecture: Modular Monolith with Domain-Driven Design principles
 * Runtime: Java 21 LTS, Spring Boot 3.x, Spring Security & Authorization Server
 * Database: PostgreSQL with Flyway migrations
 * Cache: Redis for session management
 * 
 * @version 1.0.0
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({ MfaProperties.class, NotificationProperties.class })
public class MeicryptIdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeicryptIdentityApplication.class, args);
    }
}
