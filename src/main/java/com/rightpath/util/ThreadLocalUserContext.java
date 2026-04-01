package com.rightpath.util;

import java.util.HashMap;
import java.util.Map;

import com.rightpath.entity.Users;

public class ThreadLocalUserContext {
	// Thread-local variable to store user data for the current thread
	private static final ThreadLocal<Users> userContext = new ThreadLocal<>();

	/**
	 * Internal access to the current thread's resolved user entity.
	 * Prefer using /api/me for roles & permissions; this is used only for mapping
	 * non-sensitive profile fields in the login response.
	 */
	public static Users getUserEntity() {
		return userContext.get();
	}

	/**
	 * Sets the user information in the current thread context.
	 *
	 * @param user The user object to be set in the context.
	 */
	public static void setUser(Users user) {
		userContext.set(user);
	}

	/**
	 * Retrieves a non-sensitive map of user information from the current thread
	 * context.
	 *
	 * @return A map containing non-sensitive user information.
	 */
	public static Map<String, String> getUser() {
		Users u = userContext.get();
		if (u == null) {
			return java.util.Collections.emptyMap();
		}
		return setUserNonSensitiveinfo(u);
	}

	/**
	 * Clears the user context from the current thread. This should be called after
	 * the user-specific processing is complete to avoid memory leaks.
	 */
	public static void clear() {
		userContext.remove();
	}

	/**
	 * Converts user data into a map containing only non-sensitive information. This
	 * method excludes sensitive information like passwords.
	 *
	 * @param users The user object to extract non-sensitive information from.
	 * @return A map containing non-sensitive user information.
	 */
	public static Map<String, String> setUserNonSensitiveinfo(Users users) {
		Map<String, String> userMap = new HashMap();
		userMap.put("firstName", users.getFirstName());
		userMap.put("lastName", users.getLastName());
		userMap.put("email", users.getEmail());
		userMap.put("mobileNumber", users.getMobileNumber());
		userMap.put("alternativeMobileNumber", users.getAlternativeMobileNumber());
		return userMap;

	}
}
