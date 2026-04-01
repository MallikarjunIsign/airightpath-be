package com.rightpath.service;

import java.time.LocalDateTime;
import java.util.Map;

import com.rightpath.enums.EmailType;

/**
 * Service interface for sending various types of emails related to user
 * registration, exams, OTPs, and notifications.
 */
public interface EmailService {

    /**
     * Sends a registration success email to the user upon successful sign-up.
     *
     * @param toEmail      Recipient's email address.
     * @param lastName     User's last name.
     * @param firstName    User's first name.
     * @param mobileNumber User's mobile number.
     * 
     * Log Suggestion:
     * INFO - "Sending registration success email to: {toEmail}"
     */
    void sendSuccessRegistrationEmail(String toEmail, String lastName, String firstName, String mobileNumber);

    /**
     * Sends the exam link and time window details to a candidate.
     *
     * @param email     Candidate's email address.
     * @param startTime Exam start time.
     * @param endTime   Exam end time.
     * @param jobPrefix Unique identifier/job prefix for the exam.
     * 
     * Log Suggestion:
     * INFO - "Exam link email sent to: {email}, Start: {startTime}, End: {endTime}"
     */
    void sendExamLink(String email, LocalDateTime startTime, LocalDateTime endTime, String jobPrefix);

    /**
     * Sends a one-time password (OTP) email for verification or login.
     *
     * @param to  Recipient's email address.
     * @param otp The generated OTP code.
     * @throws Exception If an error occurs while sending the email.
     * 
     * Log Suggestion:
     * INFO - "OTP email sent to: {to}"
     * ERROR - "Failed to send OTP to: {to}, Reason: {exception.getMessage()}"
     */
    void sendOtpEmail(String to, String otp) throws Exception;

    /**
     * Sends confirmation after a successful password update.
     *
     * @param to      Recipient's email address.
     * @param subject Email subject line.
     * @param text    Email body content.
     * 
     * Log Suggestion:
     * INFO - "Password update confirmation sent to: {to}"
     */
    void updatedPasswordConfirmation(String to, String subject, String text);

    /**
     * Sends an email to notify the user of successful exam completion.
     *
     * @param email     Candidate's email address.
     * @param jobPrefix Related job prefix.
     * 
     * Log Suggestion:
     * INFO - "Exam success email sent to: {email} for job: {jobPrefix}"
     */
    void sendSuccessExamAttend(String email, String jobPrefix);

    /**
     * Sends a shortlist notification to the candidate.
     *
     * @param toEmail   Candidate's email address.
     * @param fullName  Candidate's full name.
     * 
     * Log Suggestion:
     * INFO - "Shortlist notification sent to: {toEmail}, Name: {fullName}"
     */
    void sendShortlistNotification(String toEmail, String fullName);

    /**
     * Sends an HTML-formatted email (e.g., styled OTPs, notifications).
     *
     * @param to       Recipient's email address.
     * @param subject  Email subject line.
     * @param htmlBody HTML content of the email.
     * 
     * Log Suggestion:
     * INFO - "HTML email sent to: {to}, Subject: {subject}"
     * ERROR - "Failed to send HTML email to: {to}, Reason: {exception.getMessage()}"
     */
    void sendHtmlEmail(String to, String subject, String htmlBody);

    /**
     * Sends a notification when a coding exam is successfully attended.
     *
     * @param email     Candidate's email address.
     * @param jobPrefix Related job prefix.
     * 
     * Log Suggestion:
     * INFO - "Coding exam attendance email sent to: {email}, Job: {jobPrefix}"
     */
    void sendSuccessCodingExamAttend(String email, String jobPrefix);

	void sendUniversalEmail(EmailType emailType, Map<String, Object> params);
}
