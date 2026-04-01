package com.rightpath.rbac;

/**
 * Canonical role names for the system.
 *
 * NOTE: Spring Security "role" convention uses the ROLE_ prefix.
 */
public enum RoleName {
    SUPER_ADMIN,
    ADMIN,
    USER;

    public String asSecurityRole() {
        return "ROLE_" + name();
    }
}
