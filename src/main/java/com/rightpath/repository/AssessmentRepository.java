package com.rightpath.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.rightpath.entity.Assessment;
import com.rightpath.enums.AssessmentType;
import jakarta.transaction.Transactional;

/**
 * JPA Repository for managing Assessment entities and their lifecycle operations.
 * 
 * <p>Provides both derived query methods and custom JPQL/native queries for:
 * <ul>
 *   <li>Assessment retrieval by various criteria</li>
 *   <li>Expiry status management</li>
 *   <li>Exam attendance tracking</li>
 *   <li>Complex multi-parameter lookups</li>
 * </ul>
 * 
 * <p>Note: All modifying queries are transactional and include proper locking hints
 * where needed for concurrent access scenarios.
 */
@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    /**
     * Finds all assessments associated with a candidate's email.
     * 
     * @param candidateEmail The email address of the candidate
     * @return List of matching assessments (empty if none found)
     * 
     * @implNote Uses Spring Data's derived query method naming convention
     */
    List<Assessment> findByCandidateEmail(String candidateEmail);

    /**
     * Retrieves assessments that have passed their deadline but aren't marked as expired.
     * 
     * @return List of overdue assessments needing expiry processing
     * 
     * @apiNote Used by scheduled expiry jobs
     * @see Assessment#expired
     */
    @Query("SELECT a FROM Assessment a WHERE a.deadline < CURRENT_TIMESTAMP AND a.expired = false")
    List<Assessment> findExpiredAssessments();

    /**
     * Finds active (non-expired, non-attended) assessments for a candidate.
     * 
     * @param candidateEmail The candidate's email address
     * @return List of active assessments
     * 
     * @implNote Uses JPQL with explicit parameter binding
     */
    @Query("SELECT a FROM Assessment a WHERE a.candidateEmail = :candidateEmail " +
           "AND a.expired = false AND a.examAttended = false")
    List<Assessment> findActiveAssessments(@Param("candidateEmail") String candidateEmail);

    /**
     * Marks overdue assessments as expired in bulk.
     * 
     * @return Count of assessments updated
     * 
     * @apiNote Requires both @Modifying and @Transactional annotations
     * @see #findExpiredAssessments() 
     */
    @Query("UPDATE Assessment a SET a.expired = true " +
           "WHERE a.deadline < CURRENT_TIMESTAMP AND a.expired = false")
    @Modifying
    @Transactional
    int updateExpiredAssessments();

    /**
     * Finds the most recent assessment by composite key using method naming.
     * 
     * @param email Candidate email
     * @param jobPrefix Job identifier prefix
     * @param assessmentType Type of assessment
     * @return Optional containing the latest matching assessment
     * 
     * @implNote Uses Spring Data's method name derivation
     * @see #findLatestNative(String, String, String) 
     */
    Optional<Assessment> findTopByCandidateEmailAndJobPrefixAndAssessmentTypeOrderByAssignedAtDesc(
            String email,
            String jobPrefix,
            AssessmentType assessmentType
    );

    /**
     * Native SQL fallback for finding latest assessment (useful for complex queries).
     * 
     * @param email Candidate email
     * @param jobPrefix Job identifier prefix
     * @param assessmentType Type of assessment
     * @return Optional containing the latest matching assessment
     * 
     * @implNote Uses native SQL with explicit LIMIT for better control
     */
    @Query(value = """
            SELECT * FROM assessment
            WHERE candidate_email = :email
              AND job_prefix = :jobPrefix
              AND assessment_type = :type
            ORDER BY assigned_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Assessment> findLatestNative(
            @Param("email") String email,
            @Param("jobPrefix") String jobPrefix,
            @Param("type") String assessmentType
    );

    /**
     * Marks an assessment as attended by a candidate.
     * 
     * @param candidateEmail The attending candidate's email
     * @param assessmentId The assessment ID
     * 
     * @throws jakarta.persistence.OptimisticLockException if concurrent modification occurs
     * @apiNote Uses explicit parameter names for clarity
     */
    @Modifying
    @Transactional
    @Query("UPDATE Assessment a SET a.examAttended = true " +
           "WHERE a.id = :assessmentId AND a.candidateEmail = :candidateEmail")
    void markExamAttended(
            @Param("candidateEmail") String candidateEmail, 
            @Param("assessmentId") Long assessmentId
    );
    
    Optional<Assessment> findTopByJobPrefixAndCandidateEmailAndAssessmentTypeOrderByAssignedAtDesc(
            String jobPrefix, String candidateEmail, AssessmentType assessmentType);

    long countByCandidateEmailAndJobPrefixAndExamAttendedFalse(String candidateEmail, String jobPrefix);

}