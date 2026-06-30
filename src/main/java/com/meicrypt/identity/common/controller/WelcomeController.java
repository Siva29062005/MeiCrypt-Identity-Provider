package com.meicrypt.identity.common.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Welcome controller providing basic information about the application.
 * Serves the root endpoint with API information and links.
 */
@RestController
public class WelcomeController {

    @Value("${spring.application.name}")
    private String applicationName;

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> welcome() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("application", applicationName);
        response.put("version", "1.0.0-SNAPSHOT");
        response.put("status", "running");
        response.put("timestamp", Instant.now());
        response.put("phase", "Phase 0: Bootstrap & Foundation");
        
        Map<String, String> links = new LinkedHashMap<>();
        links.put("api_docs", "/swagger-ui.html");
        links.put("api_json", "/api-docs");
        links.put("health", "/actuator/health");
        links.put("info", "/actuator/info");
        
        response.put("_links", links);
        
        Map<String, String> message = new LinkedHashMap<>();
        message.put("welcome", "Welcome to MeiCrypt Identity Platform");
        message.put("description", "Enterprise Multi-Tenant Identity and Access Management");
        message.put("note", "Phase 0 - No authentication required. Use /swagger-ui.html to explore APIs.");
        
        response.put("message", message);
        
        return ResponseEntity.ok(response);
    }
}
