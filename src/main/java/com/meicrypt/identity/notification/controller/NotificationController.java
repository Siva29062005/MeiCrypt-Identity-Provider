package com.meicrypt.identity.notification.controller;

import com.meicrypt.identity.notification.dto.NotificationDTO;
import com.meicrypt.identity.notification.dto.SendNotificationRequest;
import com.meicrypt.identity.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST facade over the notification outbox (Phase 10).
 *
 * <p>All routes require the {@code audit:log:read} authority on the caller's
 * organization — notifications typically contain PII and should only be
 * inspected by administrators.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('audit:log:read')")
    public ResponseEntity<NotificationDTO> enqueue(@PathVariable UUID organizationId,
                                                   @Valid @RequestBody SendNotificationRequest request) {
        // Force the target organization to match the URL scope
        SendNotificationRequest scoped = new SendNotificationRequest(
                organizationId, request.userId(), request.channel(), request.recipient(),
                request.templateKey(), request.parameters(), request.locale(),
                request.subject(), request.body());
        NotificationDTO dto = notificationService.enqueue(scoped);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    @GetMapping
    @PreAuthorize("@rbac.sameOrganization(#organizationId) and hasAuthority('audit:log:read')")
    public ResponseEntity<List<NotificationDTO>> list(@PathVariable UUID organizationId,
                                                      @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(notificationService.listRecent(organizationId, limit));
    }
}
