package com.meicrypt.identity.auth.mapper;

import com.meicrypt.identity.auth.dto.DeviceDTO;
import com.meicrypt.identity.auth.dto.SessionDTO;
import com.meicrypt.identity.auth.entity.UserDevice;
import com.meicrypt.identity.auth.entity.UserSession;
import org.springframework.stereotype.Component;

@Component
public class SessionMapper {

    public SessionDTO toDTO(UserSession s) {
        return new SessionDTO(
                s.getId(), s.getUserId(), s.getOrganizationId(), s.getDeviceId(),
                s.getIpAddress(), s.getUserAgent(), s.getStatus(),
                s.getCreatedAt(), s.getLastActivityAt(), s.getExpiresAt()
        );
    }

    public DeviceDTO toDTO(UserDevice d) {
        return new DeviceDTO(
                d.getId(), d.getUserId(), d.getDeviceFingerprint(), d.getDeviceName(),
                d.getDeviceType(), d.getBrowser(), d.getOperatingSystem(),
                d.getLastIpAddress(), d.getLastSeenAt(), d.getTrusted(), d.getCreatedAt()
        );
    }
}
