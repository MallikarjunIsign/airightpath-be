package com.rightpath.util;

public enum Authorities {
	SUPER_ADMIN("ROLE_SUPER_ADMIN"), // Full system access.
	ADMIN("ROLE_ADMIN"), // Administrator role with elevated privileges.
	USER("ROLE_USER"); // Standard user role.

	// The name of the role associated with the authority
	private final String roleName;

	// Constructor to initialize the authority with a role name.
	Authorities(String roleName) {
		this.roleName = roleName;
	}

	/**
	 * Retrieves the role name associated with the authority.
	 *
	 * @return The role name as a string.
	 */
	public String getRoleName() {
		return roleName;
	}

	/**
	 * Overrides the default `toString` method to return the role name.
	 *
	 * @return The role name as a string.
	 */
	@Override
	public String toString() {
		return roleName;
	}
}
