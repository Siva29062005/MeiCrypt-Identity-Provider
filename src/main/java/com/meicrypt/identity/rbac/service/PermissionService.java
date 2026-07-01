package com.meicrypt.identity.rbac.service;

import com.meicrypt.identity.rbac.dto.PermissionDTO;
import com.meicrypt.identity.rbac.entity.Permission;
import com.meicrypt.identity.rbac.exception.PermissionNotFoundException;
import com.meicrypt.identity.rbac.mapper.RbacMapper;
import com.meicrypt.identity.rbac.repository.PermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only service exposing the system permission catalog (Module 4.1).
 *
 * Permissions cannot be created or mutated at runtime; only Flyway migrations
 * introduce new entries. This service therefore restricts itself to read
 * operations and provides a resolver used by {@link RoleService} to translate
 * permission codes coming from the API into managed JPA entities.
 */
@Service
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RbacMapper rbacMapper;

    public PermissionService(PermissionRepository permissionRepository, RbacMapper rbacMapper) {
        this.permissionRepository = permissionRepository;
        this.rbacMapper = rbacMapper;
    }

    public List<PermissionDTO> listAll() {
        return permissionRepository.findAll()
                .stream()
                .map(rbacMapper::toDTO)
                .toList();
    }

    public List<PermissionDTO> listByDomain(String domain) {
        return permissionRepository.findByDomain(domain)
                .stream()
                .map(rbacMapper::toDTO)
                .toList();
    }

    /**
     * Resolves a collection of permission codes into managed entities.
     * Throws if any code is unknown - callers must send codes that exist in
     * the seeded catalog.
     */
    public Set<Permission> resolveByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> requested = new HashSet<>(codes);
        Set<Permission> found = permissionRepository.findByCodeIn(requested);
        if (found.size() != requested.size()) {
            Set<String> foundCodes = new HashSet<>();
            found.forEach(p -> foundCodes.add(p.getCode()));
            requested.removeAll(foundCodes);
            throw new PermissionNotFoundException("Unknown permission codes: " + requested);
        }
        return found;
    }
}
