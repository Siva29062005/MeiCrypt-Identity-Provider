package com.meicrypt.identity.sso.dto;

import java.util.List;

/**
 * Result of a Single Logout fan-out (Module 8.2).
 */
public record LogoutResponse(
        int participantsTotal,
        int notificationsQueued,
        List<String> skippedClients
) {}
