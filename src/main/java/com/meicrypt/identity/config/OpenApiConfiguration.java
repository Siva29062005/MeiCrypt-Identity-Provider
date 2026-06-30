package com.meicrypt.identity.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger documentation configuration.
 * Configures API documentation accessible at /swagger-ui.html
 */
@Configuration
public class OpenApiConfiguration {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI meicryptIdentityOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MeiCrypt Identity Platform API")
                        .description("Enterprise Multi-Tenant Identity and Access Management Platform\n\n" +
                                "## Features\n" +
                                "- Multi-tenant organization management\n" +
                                "- User identity lifecycle management\n" +
                                "- OAuth2 and OpenID Connect support\n" +
                                "- Role-based access control (RBAC)\n" +
                                "- Multi-factor authentication (MFA)\n" +
                                "- SSO federation\n" +
                                "- Comprehensive audit logging\n\n" +
                                "## Authentication\n" +
                                "Most endpoints require authentication via Bearer tokens. " +
                                "Use the OAuth2 authorization flows to obtain access tokens.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("MeiCrypt Engineering Team")
                                .email("engineering@meicrypt.com")
                                .url("https://meicrypt.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://meicrypt.com/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.meicrypt.com")
                                .description("Production Server")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token obtained from OAuth2 authorization flows")));
    }
}
