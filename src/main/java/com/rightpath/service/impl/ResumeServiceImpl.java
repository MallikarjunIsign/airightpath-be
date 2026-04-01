package com.rightpath.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.rightpath.entity.JobPost;
import com.rightpath.entity.Resume;
import com.rightpath.entity.Users;
import com.rightpath.repository.JobPostRepository;
import com.rightpath.repository.ResumeRepository;
import com.rightpath.repository.UsersRepository;
import com.rightpath.service.ResumeService;

@Service
public class ResumeServiceImpl implements ResumeService {

	@Autowired
	private ResumeRepository resumeRepository;

	@Autowired
	private UsersRepository userRepository;
	
	@Autowired
	private JobPostRepository jobPostRepository;
	
	private final Tika tika = new Tika();

	/**
	 * Saves a new resume file for a user identified by their email.
	 *
	 * @param file  The uploaded resume file.
	 * @param email The email of the user to whom the resume belongs.
	 * @return The saved Resume entity.
	 * @throws IOException If there is an issue reading the file data.
	 */
//	@Override
//	public Resume saveResume(MultipartFile file, String email) throws IOException {
//		// Fetch the user from the database using email
//		Users user = userRepository.findById(email)
//				.orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
//
//		// Create and save the resume
//		Resume resume = new Resume();
//		resume.setFileName(file.getOriginalFilename());
//		resume.setFileType(file.getContentType());
//		resume.setData(file.getBytes());
//		resume.setUsers(user); // Link the resume to the user
//
//		return resumeRepository.save(resume);
//	}
	
	
	@Override
	public Resume saveResume(MultipartFile file, String email, String jobPrefix) throws IOException {
	    Users user = userRepository.findById(email)
	            .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
	    
	    JobPost jobPost = jobPostRepository.findByJobPrefix(jobPrefix)
	            .orElseThrow(() -> new IllegalArgumentException("Job not found with prefix: " + jobPrefix));

	    Resume resume = new Resume();
	    resume.setFileName(file.getOriginalFilename());
	    resume.setFileType(file.getContentType());
	    resume.setData(file.getBytes());
	    resume.setUsers(user);
	    resume.setJobPost(jobPost);

	    return resumeRepository.save(resume);
	}


	/**
	 * Updates an existing resume file for a user identified by their email.
	 *
	 * @param file  The updated resume file.
	 * @param email The email of the user whose resume needs to be updated.
	 * @return The updated Resume entity.
	 * @throws IOException If there is an issue reading the file data.
	 */
	@Override
	public Resume updateResume(MultipartFile file, String email) throws IOException {
		// Fetch the user by email
		Users user = userRepository.findById(email)
				.orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

		// Fetch the existing resume for the user
		Resume existingResume = resumeRepository.findByUsers(user)
				.orElseThrow(() -> new IllegalArgumentException("Resume not found for user: " + email));

		// Update the existing resume
		existingResume.setFileName(file.getOriginalFilename());
		existingResume.setFileType(file.getContentType());
		existingResume.setData(file.getBytes());

		return resumeRepository.save(existingResume);
	}

	/**
	 * Retrieves the resume associated with a user identified by their email.
	 *
	 * @param email The email of the user whose resume needs to be retrieved.
	 * @return The Resume entity.
	 * @throws IllegalArgumentException If the user or resume is not found.
	 */
	@Override
	public Resume getResumeByEmail(String email) {
		Users user = userRepository.findByEmail(email)
				.orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

		return resumeRepository.findByUsers(user)
				.orElseThrow(() -> new IllegalArgumentException("Resume not found for user: " + email));
	}

	@Override
	public List<Resume> getAllResumesWithUsers() {
		return resumeRepository.findAllWithUsers();
	}
	

	public String extractText(byte[] resumeData) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(resumeData)) {
            return tika.parseToString(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing resume data", e);
        }
    }

}