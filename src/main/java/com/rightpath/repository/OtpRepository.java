package com.rightpath.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.rightpath.entity.Otp;

import jakarta.transaction.Transactional;

@Repository
public interface OtpRepository extends JpaRepository<Otp, Long> {

	/**
	 * Retrieves an OTP record by the associated email address.
	 *
	 * @param email the email address linked to the OTP.
	 * @return an Optional containing the OTP if present, or empty if not found.
	 */
	Optional<Otp> findByEmail(String email);

	/**
	 * Deletes all OTP records where the expiration time is before the given timestamp.
	 * This is used for cleaning up expired OTP entries.
	 *
	 * NOTE:
	 * - Marked as @Transactional to ensure data integrity.
	 * - @Modifying indicates it's a write operation (DELETE).
	 *
	 * @param time the LocalDateTime used to compare expiration timestamps.
	 * @return the count of deleted OTP records.
	 */
	@Modifying
	@Transactional
	@Query("DELETE FROM Otp o WHERE o.expirationTime < :time")
	int deleteAllByExpirationTimeBefore(@Param("time") LocalDateTime time);

	/**
	 * Retrieves an OTP record by the associated mobile number.
	 *
	 * @param mobile the mobile number linked to the OTP.
	 * @return an Optional containing the OTP if present, or empty if not found.
	 */
	Optional<Otp> findByMobile(String mobile);
}
