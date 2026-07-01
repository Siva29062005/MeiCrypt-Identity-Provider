package com.meicrypt.identity.mfa.mapper;

import com.meicrypt.identity.mfa.dto.MfaFactorDTO;
import com.meicrypt.identity.mfa.entity.UserMfaFactor;
import org.springframework.stereotype.Component;

@Component
public class MfaFactorMapper {

    public MfaFactorDTO toDto(UserMfaFactor factor) {
        return new MfaFactorDTO(
                factor.getId(),
                factor.getUserId(),
                factor.getFactorType(),
                factor.getDisplayName(),
                factor.getStatus(),
                factor.isPrimary(),
                factor.getCreatedAt(),
                factor.getActivatedAt(),
                factor.getLastUsedAt()
        );
    }
}
