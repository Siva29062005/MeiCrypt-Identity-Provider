package com.meicrypt.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password encoder configuration.
 * Uses BCrypt with strength 12 for secure password hashing.
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt password encoder bean
     * Strength 12 provides good balance between security and performance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
