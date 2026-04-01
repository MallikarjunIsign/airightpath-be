package com.rightpath.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.rightpath.entity.Users;
import com.rightpath.rbac.RoleName;

@Repository
public interface UsersRepository extends JpaRepository<Users, String> {

	/**
	 * Finds a user by their email.
	 *
	 * @param email The email of the user to be retrieved.
	 * @return An `Optional` containing the user if found, or empty if no user
	 *         exists with the specified email.
	 */
	Optional<Users> findByEmail(String email);
	
	

	/**
	 * Soft deletes a user by updating their `enabled` status to `false`.
	 *
	 * @param email The email of the user to be disabled.
	 */
	@Transactional
	@Modifying
	@Query("update Users e set e.enabled = false where e.email = :email")
	public void deleteByEmail(@Param("email") String email);

	/**
	 * Activates a user by updating their `enabled` status to `true`.
	 *
	 * @param email The email of the user to be enabled.
	 */
	@Transactional
	@Modifying
	@Query("update Users e set e.enabled = true where e.email = :email")
	public void activeByEmail(@Param("email") String email);

	/**
	 * Retrieves all non-admin users by checking that a specific role is not in
	 * their RBAC roles.
	 *
	 * @param adminRole The admin role to be excluded.
	 * @return A list of all users who do not have the admin role.
	 */
	@Query("SELECT DISTINCT u FROM Users u WHERE u.email NOT IN (" +
			"SELECT ur.user.email FROM UserRole ur WHERE ur.active = true AND ur.role.name = :adminRole)")
	List<Users> findAllNonAdminUsers(@Param("adminRole") RoleName adminRole);

	/**
	 * Updates a user's password by their email.
	 *
	 * @param email       The email of the user whose password is being updated.
	 * @param newPassword The new password to set.
	 */
	@Transactional
	@Modifying
	@Query("update Users e set e.password = :newPassword where e.email = :email")
	void updatePasswordByEmail(@Param("email") String email, @Param("newPassword") String newPassword);

	boolean existsByEmail(String email);
	boolean existsByMobileNumber(String mobileNumber);
	
	Optional<Users> findByMobileNumber(String mobile);
}
