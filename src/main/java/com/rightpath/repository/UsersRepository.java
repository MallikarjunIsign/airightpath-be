package com.rightpath.repository;

import java.util.Collection;
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
	 * Users holding none of the given roles through an active assignment.
	 *
	 * <p>Used for the candidate list, where the roles excluded are the staff
	 * ones. Takes a collection rather than the single {@code RoleName} it used
	 * to: excluding only ADMIN left every SUPER_ADMIN on the candidate list as
	 * well as the staff list, so the same account was administered from two
	 * screens. The complement of {@link #findAllByActiveRoleIn(Collection)} —
	 * pass both the same roles and no account can fall through or appear twice.</p>
	 *
	 * @param roleNames roles that disqualify a user; an empty collection returns everyone
	 * @return matching users, each once, ordered by email
	 */
	@Query("SELECT DISTINCT u FROM Users u WHERE u.email NOT IN (" +
			"SELECT ur.user.email FROM UserRole ur WHERE ur.active = true AND ur.role.name IN :roleNames) " +
			"ORDER BY u.email ASC")
	List<Users> findAllExcludingActiveRoleIn(@Param("roleNames") Collection<RoleName> roleNames);

	/**
	 * Users holding any of the given roles through an active assignment.
	 *
	 * <p>The counterpart to {@link #findAllExcludingActiveRoleIn(Collection)}.
	 * Staff administration needs this selection — the accounts that <em>do</em>
	 * hold ADMIN or SUPER_ADMIN — and there was no way to ask for it.</p>
	 *
	 * <p>Matches on {@code active = true}, so a revoked assignment does not keep
	 * someone on the staff list. {@code DISTINCT} because an account holding both
	 * roles would otherwise appear twice, and ordered by email so the list does
	 * not reshuffle between requests.</p>
	 *
	 * @param roleNames roles to match; an empty collection returns nothing
	 * @return matching users, each once, ordered by email
	 */
	@Query("SELECT DISTINCT u FROM Users u WHERE u.email IN (" +
			"SELECT ur.user.email FROM UserRole ur WHERE ur.active = true AND ur.role.name IN :roleNames) " +
			"ORDER BY u.email ASC")
	List<Users> findAllByActiveRoleIn(@Param("roleNames") Collection<RoleName> roleNames);

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
