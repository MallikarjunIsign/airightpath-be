package com.rightpath.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.rightpath.entity.JobPost;
import com.rightpath.entity.Resume;
import com.rightpath.entity.Users;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {

	/**
	 * Retrieves the resume associated with a given user.
	 *
	 * @param user The user entity whose resume is to be fetched.
	 * @return An Optional containing the Resume if it exists, or empty if not found.
	 */
	Optional<Resume> findByUsers(Users user);

	/**
	 * Retrieves all resumes with their associated user information eagerly loaded.
	 *
	 * This query uses JOIN FETCH to avoid the N+1 select problem and ensures that
	 * user details are fetched along with the resumes.
	 *
	 * @return A list of all resumes with their user information.
	 */
	@Query("SELECT r FROM Resume r JOIN FETCH r.users")
	List<Resume> findAllWithUsers();

	/**
	 * Counts how many resumes have been submitted for a particular job post.
	 *
	 * @param jobPost The job post entity for which resume count is required.
	 * @return The number of resumes linked to the given job post.
	 */
	int countByJobPost(JobPost jobPost);
}
