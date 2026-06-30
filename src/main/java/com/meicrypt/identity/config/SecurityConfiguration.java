package com.meicrypt.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for MeiCrypt Identity Platform.
 * 
 * Phase 0: Permissive configuration for development and testing.
 * Authentication and authorization will be implemented in later phases.
 * 
 * Future Phases:
 * - Phase 3: Authentication & Session Management
 * - Phase 4: RBAC Authorization Framework
 * - Phase 6-7: OAuth2 & OpenID Connect
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.disable())
            );
        
        return http.build();
    }
}
