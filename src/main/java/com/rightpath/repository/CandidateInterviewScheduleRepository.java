package com.rightpath.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.rightpath.entity.CandidateInterviewSchedule;
import com.rightpath.enums.AttemptStatus;

public interface CandidateInterviewScheduleRepository extends JpaRepository<CandidateInterviewSchedule, Long> {

	Optional<CandidateInterviewSchedule> findByJobPrefixAndEmail(String jobPrefix, String email);

	Optional<CandidateInterviewSchedule> findFirstByJobPrefixAndEmailOrderByAssignedAtDesc(String jobPrefix, String email);

	List<CandidateInterviewSchedule> findByEmailAndAttemptStatus(String email, String attemptStatus);

	// Optional: With deadline validation
		@Query("SELECT c FROM CandidateInterviewSchedule c WHERE c.email = :email AND c.attemptStatus = com.rightpath.enums.AttemptStatus.NOT_ATTEMPTED")
	List<CandidateInterviewSchedule> findActiveInterviewsByEmail(String email);

	List<CandidateInterviewSchedule> findAllByJobPrefix(String jobPrefix);

	// Security: ownership check
	Optional<CandidateInterviewSchedule> findByIdAndEmail(Long id, String email);

	// Resume: find by id and status
	Optional<CandidateInterviewSchedule> findByIdAndAttemptStatus(Long id, AttemptStatus attemptStatus);

	// Timeout: find stale in-progress interviews
	List<CandidateInterviewSchedule> findByAttemptStatusAndStartedAtBefore(AttemptStatus attemptStatus, LocalDateTime cutoff);
	
	Optional<CandidateInterviewSchedule> findTopByJobPrefixAndEmailOrderByAssignedAtDesc(
	        String jobPrefix, String email);
}
