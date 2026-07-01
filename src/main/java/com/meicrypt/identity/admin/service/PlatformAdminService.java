package com.meicrypt.identity.admin.service;

import com.meicrypt.identity.admin.dto.PlatformOrganizationSummaryDTO;
import com.meicrypt.identity.admin.dto.PlatformStatsDTO;
import com.meicrypt.identity.admin.dto.UpdateOrganizationStatusRequest;
import com.meicrypt.identity.application.repository.ClientApplicationRepository;
import com.meicrypt.identity.audit.entity.AuditActorType;
import com.meicrypt.identity.audit.entity.AuditStatus;
import com.meicrypt.identity.audit.service.AuditService;
import com.meicrypt.identity.auth.entity.SessionStatus;
import com.meicrypt.identity.auth.repository.UserSessionRepository;
import com.meicrypt.identity.common.exception.ResourceNotFoundException;
import com.meicrypt.identity.notification.entity.NotificationStatus;
import com.meicrypt.identity.notification.repository.NotificationRepository;
import com.meicrypt.identity.organization.entity.Organization;
import com.meicrypt.identity.organization.entity.OrganizationStatus;
import com.meicrypt.identity.organization.repository.OrganizationRepository;
import com.meicrypt.identity.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform-wide administration service (Phase 12 - Module 12.1).
 *
 * <p>Every mutation performed via this service must be wrapped in an audit
 * event so platform staff activity is fully traceable. Reads do not emit
 * audit events by default.
 */
@Service
public class PlatformAdminService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final NotificationRepository notificationRepository;
    private final ClientApplicationRepository applicationRepository;
    private final AuditService auditService;

    public PlatformAdminService(OrganizationRepository organizationRepository,
                                UserRepository userRepository,
                                UserSessionRepository sessionRepository,
                                NotificationRepository notificationRepository,
                                ClientApplicationRepository applicationRepository,
                                AuditService auditService) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.notificationRepository = notificationRepository;
        this.applicationRepository = applicationRepository;
        this.auditService = auditService;
    }

    // ---- READ PATHS ---------------------------------------------------

    @Transactional(readOnly = true)
    public PlatformStatsDTO getPlatformStats() {
        long orgCount = organizationRepository.count();
        long activeOrgs = organizationRepository.countByStatus(OrganizationStatus.ACTIVE);
        long users = userRepository.count();
        long activeSessions = sessionRepository.countByStatus(SessionStatus.ACTIVE);
        long pending = notificationRepository.countByStatus(NotificationStatus.PENDING);
        long apps = applicationRepository.count();
        return new PlatformStatsDTO(orgCount, activeOrgs, users, activeSessions, pending, apps);
    }

    @Transactional(readOnly = true)
    public List<PlatformOrganizationSummaryDTO> listOrganizations() {
        return organizationRepository.findAll().stream()
                .map(o -> PlatformOrganizationSummaryDTO.of(
                        o,
                        userRepository.countByOrganizationId(o.getId()),
                        sessionRepository.countByOrganizationIdAndStatus(
                                o.getId(), SessionStatus.ACTIVE)))
                .toList();
    }

    // ---- MUTATIONS ----------------------------------------------------

    @Transactional
    public PlatformOrganizationSummaryDTO updateOrganizationStatus(
            UUID organizationId, UpdateOrganizationStatusRequest request) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization", organizationId.toString()));
        OrganizationStatus previous = org.getStatus();
        org.setStatus(request.status());
        organizationRepository.save(org);

        Map<String, Object> meta = new HashMap<>();
        meta.put("previousStatus", previous.name());
        meta.put("newStatus", request.status().name());
        if (request.reason() != null && !request.reason().isBlank()) {
            meta.put("reason", request.reason());
        }
        auditService.recordForActor(
                "PLATFORM_ORGANIZATION_STATUS_CHANGED",
                AuditStatus.SUCCESS,
                organizationId,
                null,
                null,
                AuditActorType.USER,
                "ORGANIZATION",
                organizationId.toString(),
                meta);

        return PlatformOrganizationSummaryDTO.of(
                org,
                userRepository.countByOrganizationId(org.getId()),
                sessionRepository.countByOrganizationIdAndStatus(
                        org.getId(), SessionStatus.ACTIVE));
    }
}
