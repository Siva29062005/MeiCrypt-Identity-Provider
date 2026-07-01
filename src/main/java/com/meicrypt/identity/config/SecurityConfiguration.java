package com.meicrypt.identity.config;

import com.meicrypt.identity.auth.config.JwtProperties;
import com.meicrypt.identity.auth.security.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration.
 *
 * Phase 3 hardening:
 *   - Stateless JWT authentication via {@link JwtAuthenticationFilter}.
 *   - Public routes: /api/v1/auth/**, /api/v1/users/register, verification & password reset,
 *     Swagger, actuator health, and error paths.
 *   - Everything else requires an authenticated principal.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .authorizeHttpRequests(auth -> auth
                        // Public - authentication endpoints
                        .requestMatchers("/api/v1/auth/login",
                                         "/api/v1/auth/refresh",
                                         "/api/v1/auth/logout").permitAll()
                        // Public - onboarding
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/register").permitAll()
                        .requestMatchers("/api/v1/verification/**").permitAll()
                        .requestMatchers("/api/v1/password-reset/**").permitAll()
                        // Public - documentation & health
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                         "/api-docs/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/", "/error", "/favicon.ico").permitAll()
                        // Phase 6 - OAuth2 endpoints. /authorize needs the
                        // upstream Phase 3 JWT filter to populate the principal,
                        // but is protocol-level public (RFC 6749).
                        .requestMatchers("/oauth2/authorize",
                                         "/oauth2/token",
                                         "/oauth2/revoke",
                                         "/oauth2/introspect").permitAll()
                        // Phase 7 - OIDC discovery + JWKS. Must be publicly
                        // reachable so relying parties can bootstrap without
                        // credentials (OIDC Discovery 1.0 §4, RFC 8414 §3).
                        .requestMatchers("/.well-known/openid-configuration",
                                         "/.well-known/jwks.json").permitAll()
                        // Phase 8 - RP-Initiated Logout. Must be public so an
                        // unauthenticated browser can still be redirected
                        // back to the client's post_logout_redirect_uri
                        // (OIDC RP-Initiated Logout 1.0 §3).
                        .requestMatchers("/oauth2/logout").permitAll()

                        // Phase 9 - MFA challenge redemption. The caller has
                        // *not* yet completed the login flow (only password
                        // was accepted) so this endpoint must be publicly
                        // reachable and rely entirely on the opaque
                        // challenge_token for authorization.
                        .requestMatchers("/api/v1/mfa/challenges/verify").permitAll()

                        // Everything else must be authenticated
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
