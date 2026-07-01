package com.meicrypt.identity.rbac.entity;

/**
 * Distinguishes the origin of a role.
 *
 * <ul>
 *   <li>{@link #SYSTEM} - provisioned automatically for every organization.
 *       Cannot be renamed, deleted, or have its permissions edited.</li>
 *   <li>{@link #CUSTOM} - freely defined by organization administrators.</li>
 * </ul>
 */
public enum RoleType {
    SYSTEM,
    CUSTOM
}
