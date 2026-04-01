package com.rightpath.service;

import java.io.IOException;

import org.apache.tika.exception.TikaException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for handling Applicant Tracking System (ATS) operations,
 * including resume and job description parsing, score evaluation,
 * and candidate notification.
 */
public interface ATSService {

    /**
     * Processes the provided resume and job description to calculate a matching score.
     * The score evaluates factors like skill alignment, keyword match, and experience relevance.
     *
     * @param resume         MultipartFile representing the candidate's resume (PDF, DOC, DOCX).
     * @param jobDescription The job description text used for comparison.
     * @return A match score ranging from 0.0 to 100.0, indicating how well the resume matches the job description.
     * @throws IOException   If file reading or parsing fails.
     * @throws TikaException If text extraction from the file fails using Apache Tika.
     *
     * Log Suggestion (for implementing class):
     * - INFO: "Processing ATS match for resume: {resume file name} against job description."
     * - DEBUG: "Extracted resume and job description text. Calculating match score..."
     * - INFO: "Calculated ATS score: {score} for resume: {resume file name}"
     */
    double processFiles(MultipartFile resume, String jobDescription) throws IOException, TikaException;

    /**
     * Evaluates the candidate’s ATS score and sends an email notification with their results.
     * If the score meets a certain threshold, they may be shortlisted.
     *
     * @param toEmail   Candidate's email address.
     * @param fullName  Candidate's full name.
     * @param atsScore  ATS score obtained from matching process.
     *
     * Log Suggestion:
     * - INFO: "Evaluating ATS score for {fullName} ({toEmail})"
     * - DEBUG: "ATS Score: {atsScore} — Triggering email notification"
     * - INFO: "ATS evaluation email sent to {toEmail}"
     */
    void evaluateScoreAndNotify(String toEmail, String fullName, double atsScore);

    /**
     * Sends a shortlist notification to the candidate when they qualify based on their ATS score.
     *
     * @param toEmail  Candidate's email address.
     * @param fullName Candidate's full name.
     *
     * Log Suggestion:
     * - INFO: "Shortlist notification triggered for {fullName} ({toEmail})"
     * - DEBUG: "Sending shortlist email to candidate"
     * - INFO: "Shortlist email successfully sent to {toEmail}"
     */
    void sendShortlistNotification(String toEmail, String fullName);
}
