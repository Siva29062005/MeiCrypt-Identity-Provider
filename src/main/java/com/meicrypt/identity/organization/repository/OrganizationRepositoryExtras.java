package com.meicrypt.identity.organization.repository;

import com.meicrypt.identity.organization.entity.OrganizationStatus;

/**
 * Marker interface documenting the platform-admin query surface expected
 * of {@link OrganizationRepository}. The repository itself extends this
 * (see {@link OrganizationRepository}) so no separate implementation is
 * required — this file exists to keep the additions to a repository
 * scoped and reviewable.
 */
public interface OrganizationRepositoryExtras {
    long countByStatus(OrganizationStatus status);
}
