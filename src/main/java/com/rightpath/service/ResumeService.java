package com.rightpath.service;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.rightpath.entity.Resume;

public interface ResumeService {

    /**
     * Saves a new resume file for the specified email and job prefix.
     *
     * @param file      The resume file to be saved (PDF, DOC, DOCX).
     * @param email     The email associated with the resume.
     * @param jobPrefix The job prefix to associate with the resume.
     * @return The saved Resume entity.
     * @throws IOException If an error occurs during file upload or processing.
     */
    Resume saveResume(MultipartFile file, String email, String jobPrefix) throws IOException;

    /**
     * Updates an existing resume for the specified email.
     *
     * @param file  The new resume file to replace the existing one.
     * @param email The email associated with the resume.
     * @return The updated Resume entity.
     * @throws IOException If an error occurs during file processing.
     */
    Resume updateResume(MultipartFile file, String email) throws IOException;

    /**
     * Retrieves the resume associated with a given email.
     *
     * @param email The email for which the resume is being retrieved.
     * @return The Resume entity.
     */
    Resume getResumeByEmail(String email);

    /**
     * Retrieves all resumes along with associated user information.
     *
     * @return List of Resume entities with linked user data.
     */
    List<Resume> getAllResumesWithUsers();

    /**
     * Extracts plain text content from a resume's binary data.
     *
     * @param resumeData The binary resume content.
     * @return Extracted text content from the resume.
     */
    String extractText(byte[] resumeData);
    
    /*
     * ===== Update Logs =====
     * ✅ Added jobPrefix as a parameter in saveResume() to associate resume with specific job posts.
     * ✅ Added extractText(byte[] resumeData) method to enable content extraction (e.g., for ATS processing).
     * ✅ Enhanced all method-level JavaDocs for clarity and developer usability.
     */
}
