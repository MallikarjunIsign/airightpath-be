package com.rightpath.service;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.rightpath.dto.ResumeDownload;
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
     * Retrieves the resume the candidate submitted with their job application, as a
     * downloadable payload (bytes + filename + content type).
     *
     * <p>Reads {@code JobApplicationForCandidate.resumeData} — the same source the
     * ATS engine scores — so a candidate who has an ATS score can always view their
     * resume. If the candidate has applied to several jobs, the most recently
     * submitted resume-bearing application is chosen deterministically.
     *
     * @param email the candidate's email
     * @return the resume payload for download
     * @throws com.rightpath.exceptions.ResourceNotFoundException if the candidate
     *         has no application carrying a resume (surfaced as HTTP 404)
     */
    ResumeDownload getResumeForDownload(String email);

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
