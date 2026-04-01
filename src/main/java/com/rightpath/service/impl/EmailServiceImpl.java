package com.rightpath.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.rightpath.entity.JobApplicationForCandidate;
import com.rightpath.entity.Users;
import com.rightpath.enums.EmailType;
import com.rightpath.repository.JobApplicationForCandidateRepository;
import com.rightpath.repository.UsersRepository;
import com.rightpath.service.EmailService;
import com.rightpath.service.WhatsAppService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final WhatsAppService whatsAppService;
    private final UsersRepository userRepository;
    private final JobApplicationForCandidateRepository jobApplicationRepository;

    private static final String LOGO_CID = "rightpath-logo";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Autowired
    public EmailServiceImpl(JavaMailSender mailSender, WhatsAppService whatsAppService,
                          UsersRepository userRepository, JobApplicationForCandidateRepository jobApplicationRepository) {
        this.mailSender = mailSender;
        this.whatsAppService = whatsAppService;
        this.userRepository = userRepository;
        this.jobApplicationRepository = jobApplicationRepository;
    }

    
    private void sendWhatsAppNotification(EmailType emailType, Map<String, Object> params) {
        try {
            String mobileNumber = (String) params.get("mobileNumber");
            if (mobileNumber == null || mobileNumber.isEmpty()) {
                return;
            }

            switch (emailType) {
                case REGISTRATION_SUCCESS:
                    whatsAppService.sendWhatsAppMessage(
                        mobileNumber,
                        WhatsAppService.MessageType.REGISTRATION_SUCCESS,
                        params.get("firstName"),
                        params.get("lastName")
                    );
                    break;
                    
                case EXAM_SCHEDULE:
                    whatsAppService.sendWhatsAppMessage(
                        mobileNumber,
                        WhatsAppService.MessageType.EXAM_SCHEDULE,
                        params.get("startTime"),
                        params.get("endTime")
                    );
                    break;
                    
                case EXAM_SUBMISSION:
                    whatsAppService.sendWhatsAppMessage(
                        mobileNumber,
                        WhatsAppService.MessageType.EXAM_SUBMISSION
                    );
                    break;
                    
                case CODING_EXAM_SUBMISSION:
                    whatsAppService.sendWhatsAppMessage(
                        mobileNumber,
                        WhatsAppService.MessageType.CODING_EXAM_SUBMISSION
                    );
                    break;
                    
                case PASSWORD_UPDATED:
                    whatsAppService.sendWhatsAppMessage(
                        mobileNumber,
                        WhatsAppService.MessageType.PASSWORD_UPDATE
                    );
                    break;
                    
                case SHORTLIST_NOTIFICATION:
                    whatsAppService.sendWhatsAppMessage(
                        mobileNumber,
                        WhatsAppService.MessageType.SHORTLIST,
                        params.get("fullName")
                    );
                    break;
                    
                case APPLICATION_SUCCESS:
                    whatsAppService.sendWhatsAppMessage(
                        mobileNumber,
                        WhatsAppService.MessageType.JOB_APPLIED,
                        params.get("firstName"),
                        params.get("lastName"),
                        params.get("jobPrefix"),
                        params.get("jobTitle")
                    );
                    break;
                    
                case ACKNOWLEDGEMENT:
                    whatsAppService.sendWhatsAppMessage(
                        mobileNumber,
                        WhatsAppService.MessageType.EXAM_SCHEDULE,
                        params.get("startTime"),
                        params.get("endTime")
                    );
                    break;
                    
                case REJECTION:
                    whatsAppService.sendWhatsAppMessage(
                        mobileNumber,
                        WhatsAppService.MessageType.REJECTION,
                        params.get("firstName") + " " + params.get("lastName")
                    );
                    break;
                    
                case WRITTEN_TEST_SUCCESS:
                    whatsAppService.sendWhatsAppMessage(
                        mobileNumber,
                        WhatsAppService.MessageType.SHORTLIST,
                        params.get("firstName") + " " + params.get("lastName")
                    );
                    break;
                    
                case WRITTEN_TEST_FAILURE:
                    whatsAppService.sendWhatsAppMessage(
                        mobileNumber,
                        WhatsAppService.MessageType.REJECTION
                    );
                    break;
                    
               
            }
        } catch (Exception e) {
            System.err.println("Failed to send WhatsApp notification: " + e.getMessage());
        }
    }

    // The original methods can now delegate to the universal method
    @Override
    public void sendSuccessRegistrationEmail(String toEmail, String lastName, String firstName, String mobileNumber) {
        Map<String, Object> params = new HashMap<>();
        params.put("recipientEmail", toEmail);
        params.put("firstName", firstName);
        params.put("lastName", lastName);
        params.put("mobileNumber", mobileNumber);
        sendUniversalEmail(EmailType.REGISTRATION_SUCCESS, params);
    }

    @Override
    public void sendExamLink(String email, LocalDateTime startTime, LocalDateTime endTime, String jobPrefix) {
        List<JobApplicationForCandidate> applications = jobApplicationRepository
            .findByJobPrefixAndEmail(jobPrefix, email);
        
        if (!applications.isEmpty()) {
            JobApplicationForCandidate application = applications.get(0);
            Map<String, Object> params = new HashMap<>();
            params.put("recipientEmail", email);
            params.put("startTime", startTime);
            params.put("endTime", endTime);
            params.put("mobileNumber", application.getMobileNumber());
            sendUniversalEmail(EmailType.EXAM_SCHEDULE, params);
        }
    }

    @Override
    public void sendOtpEmail(String to, String otp) {
        Optional<Users> user = userRepository.findByEmail(to);
        Map<String, Object> params = new HashMap<>();
        params.put("recipientEmail", to);
        params.put("otp", otp);
        user.ifPresent(u -> params.put("mobileNumber", u.getMobileNumber()));
        sendUniversalEmail(EmailType.OTP, params);
    }

    @Override
    public void updatedPasswordConfirmation(String to, String subject, String text) {
        Optional<Users> user = userRepository.findByEmail(to);
        Map<String, Object> params = new HashMap<>();
        params.put("recipientEmail", to);
        params.put("message", text);
        user.ifPresent(u -> params.put("mobileNumber", u.getMobileNumber()));
        sendUniversalEmail(EmailType.PASSWORD_UPDATED, params);
    }

    @Override
    public void sendSuccessExamAttend(String email, String jobPrefix) {
        List<JobApplicationForCandidate> applications = jobApplicationRepository
            .findByJobPrefixAndEmail(jobPrefix, email);
        
        if (!applications.isEmpty()) {
            JobApplicationForCandidate application = applications.get(0);
            Map<String, Object> params = new HashMap<>();
            params.put("recipientEmail", email);
            params.put("mobileNumber", application.getMobileNumber());
            sendUniversalEmail(EmailType.EXAM_SUBMISSION, params);
        }
    }

    @Override
    public void sendSuccessCodingExamAttend(String email, String jobPrefix) {
        List<JobApplicationForCandidate> applications = jobApplicationRepository
            .findByJobPrefixAndEmail(jobPrefix, email);
        
        if (!applications.isEmpty()) {
            JobApplicationForCandidate application = applications.get(0);
            Map<String, Object> params = new HashMap<>();
            params.put("recipientEmail", email);
            params.put("mobileNumber", application.getMobileNumber());
            sendUniversalEmail(EmailType.CODING_EXAM_SUBMISSION, params);
        }
    }

    @Override
    public void sendShortlistNotification(String toEmail, String fullName) {
        Map<String, Object> params = new HashMap<>();
        params.put("recipientEmail", toEmail);
        params.put("fullName", fullName);
        sendUniversalEmail(EmailType.SHORTLIST_NOTIFICATION, params);
    }
    
    @Override
    public void sendUniversalEmail(EmailType emailType, Map<String, Object> params) {
        String recipientEmail = (String) params.get("recipientEmail");
        String subject = "";
        String htmlBody = "";

        switch (emailType) {
            case REGISTRATION_SUCCESS:
                subject = "RightPath – Registration Successful";
                htmlBody = String.format("""
                    <html><body style='font-family:Arial,sans-serif;background:#f9f9f9;padding:20px;'>
                      <div style='max-width:600px;margin:auto;background:#ffffff;padding:30px;border-radius:8px;'>
                        %s
                        <h2 style='color:#0056b3;text-align:center;margin-top:0'>Welcome to RightPath!</h2>
                        <p>Dear <strong>%s&nbsp;%s</strong>,</p>
                        <p>Your account has been created successfully. You can now log in to explore resources, track applications, and grow your career with RightPath.</p>
                        <p style='margin-top:30px'>Best regards,<br/><strong>The RightPath Team</strong></p>
                      </div>
                    </body></html>
                    """, getLogoHtml(), params.get("firstName"), params.get("lastName"));
                break;

            case EXAM_SCHEDULE:
                LocalDateTime startTime = (LocalDateTime) params.get("startTime");
                LocalDateTime endTime = (LocalDateTime) params.get("endTime");
                subject = "Your Upcoming Exam Schedule – RightPath";
                htmlBody = String.format("""
                    <html><body style='font-family:Arial,sans-serif;background:#f9f9f9;padding:20px;'>
                      <div style='max-width:600px;margin:auto;background:#ffffff;padding:30px;border-radius:8px;'>
                        %s
                        <h2 style='color:#2c3e50;text-align:center;margin-top:0'>Exam Scheduled</h2>
                        <p>Dear Candidate,</p>
                        <p>Please review the schedule below and be ready ahead of time:</p>
                        <table style='width:100%%;border-collapse:collapse;margin:20px 0'>
                          <tr><th style='padding:8px;border:1px solid #ddd;background:#f4f4f4'>Date</th><td style='padding:8px;border:1px solid #ddd'>%s</td></tr>
                          <tr><th style='padding:8px;border:1px solid #ddd;background:#f4f4f4'>Start&nbsp;Time</th><td style='padding:8px;border:1px solid #ddd'>%s</td></tr>
                          <tr><th style='padding:8px;border:1px solid #ddd;background:#f4f4f4'>End&nbsp;Time</th><td style='padding:8px;border:1px solid #ddd'>%s</td></tr>
                        </table>
                        <p>Log in 10&nbsp;minutes early and click <em>Start&nbsp;Exam</em>. Ensure a stable connection and quiet environment.</p>
                        <p style='margin-top:30px'>Good luck!<br/><strong>The RightPath Exams Team</strong></p>
                      </div>
                    </body></html>
                    """, getLogoHtml(), 
                    startTime.format(DATE_FORMATTER), 
                    startTime.format(TIME_FORMATTER), 
                    endTime.format(TIME_FORMATTER));
                break;

            case OTP:
                subject = "Your OTP for Password Reset – RightPath";
                htmlBody = String.format("""
                    <html><body style='font-family:Arial,sans-serif;background:#f9f9f9;padding:20px;'>
                      <div style='max-width:600px;margin:auto;background:#ffffff;padding:30px;border-radius:8px;'>
                        %s
                        <h2 style='color:#2c3e50;text-align:center;margin-top:0'>Password Reset Verification</h2>
                        <p>Use the OTP below to proceed:</p>
                        <p style='font-size:32px;font-weight:bold;color:#0056b3;text-align:center;margin:20px 0'>%s</p>
                        <p>The OTP is valid for <strong>5 minutes</strong>. Ignore this email if you didn't request a reset.</p>
                        <p style='margin-top:30px'>Stay secure,<br/><strong>The RightPath Security Team</strong></p>
                      </div>
                    </body></html>
                    """, getLogoHtml(), params.get("otp"));
                break;

            case PASSWORD_UPDATED:
                subject = "Password Updated Successfully – RightPath";
                htmlBody = String.format("""
                    <html><body style='font-family:Arial,sans-serif;background:#f9f9f9;padding:20px;'>
                      <div style='max-width:600px;margin:auto;background:#ffffff;padding:30px;border-radius:8px;'>
                        %s
                        <h2 style='color:#2c3e50;text-align:center;margin-top:0'>Password Updated Successfully</h2>
                        <p>%s</p>
                        <p>If you did not perform this change, reset your password immediately and contact our support team.</p>
                        <p style='margin-top:30px'>Kind regards,<br/><strong>The RightPath Security Team</strong></p>
                      </div>
                    </body></html>
                    """, getLogoHtml(), params.get("message"));
                break;

            case EXAM_SUBMISSION:
                subject = "Exam Submitted Successfully – RightPath";
                htmlBody = String.format("""
                    <html><body style='font-family:Arial,sans-serif;background:#f9f9f9;padding:20px;'>
                      <div style='max-width:600px;margin:auto;background:#ffffff;padding:30px;border-radius:8px;'>
                        %s
                        <h2 style='color:#2c3e50;text-align:center;margin-top:0'>Your Aptitude Exam Has Been Submitted</h2>
                        <p>Thank you for completing your exam at RightPath.</p>
                        <p>Our evaluation team will review your answers and contact you within <strong>5–7&nbsp;business days</strong> if you qualify for the next stage.</p>
                        <p style='margin-top:30px'>Good luck!<br/><strong>The RightPath Exams Team</strong></p>
                      </div>
                    </body></html>
                    """, getLogoHtml());
                break;

            case CODING_EXAM_SUBMISSION:
                subject = "Exam Submitted Successfully – RightPath";
                htmlBody = String.format("""
                    <html><body style='font-family:Arial,sans-serif;background:#f9f9f9;padding:20px;'>
                      <div style='max-width:600px;margin:auto;background:#ffffff;padding:30px;border-radius:8px;'>
                        %s
                        <h2 style='color:#2c3e50;text-align:center;margin-top:0'>Your Coding Exam Has Been Submitted</h2>
                        <p>Thank you for completing your exam at RightPath.</p>
                        <p>Our evaluation team will review your answers and contact you within <strong>5–7&nbsp;business days</strong> if you qualify for the next stage.</p>
                        <p style='margin-top:30px'>Good luck!<br/><strong>The RightPath Exams Team</strong></p>
                      </div>
                    </body></html>
                    """, getLogoHtml());
                break;

            case SHORTLIST_NOTIFICATION:
                subject = "Your Resume Has Been Shortlisted – RightPath";
                htmlBody = String.format("""
                    <html><body style='font-family:Arial,sans-serif;background:#f9f9f9;padding:20px;'>
                      <div style='max-width:600px;margin:auto;background:#ffffff;padding:30px;border-radius:8px;'>
                        %s
                        <h2 style='color:#2c3e50;text-align:center;margin-top:0'>Congratulations, %s!</h2>
                        <p>Your resume has been <strong>shortlisted</strong> for the position you applied for at <strong>RightPath</strong>.</p>
                        <p>Our hiring team will contact you soon with details of the next steps.</p>
                        <p>If you have any questions, reply to this email.</p>
                        <p style='margin-top:30px'>Warm regards,<br/><strong>The RightPath Recruitment Team</strong></p>
                      </div>
                    </body></html>
                    """, getLogoHtml(), params.get("fullName"));
                break;

            case APPLICATION_SUCCESS:
                subject = "Your Job Application at RightPath Has Been Received!";
                htmlBody = String.format("""
                    <html>
                      <body style='font-family: Arial, sans-serif; background-color: #f9f9f9; padding: 20px;'>
                        <div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); padding: 30px;'>
                          %s
                          <h2 style='color: #2E86C1;'>🎉 Thank You for Applying!</h2>
                          <p>Hi <strong>%s %s</strong>,</p>
                          <p>We have received your application for <strong>%s (%s)</strong> at <strong>RightPath</strong>.</p>
                          <p>Our recruitment team will review your application and contact you if you're shortlisted.</p>
                          <hr style='border: 0; height: 1px; background: #e0e0e0; margin: 20px 0;' />
                          <p style='margin-top: 30px;'>Regards,<br/><strong>RightPath Recruitment Team</strong></p>
                        </div>
                      </body>
                    </html>
                    """,
                    getLogoHtml(),
                    params.get("firstName"),
                    params.get("lastName"),
                    params.get("jobTitle"),
                    params.get("jobPrefix")
                );
                break;

            case ACKNOWLEDGEMENT:
                subject = "Test Schedule Acknowledgement - RightPath";
                htmlBody = String.format("""
                    <html>
                      <body style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>
                        <div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); padding: 30px;'>
                          %s
                          <h2 style='color: #2c3e50;'>Your Test Schedule</h2>
                          <p>Hi <strong>%s %s</strong>,</p>
                          <p>You are shortlisted for <strong>%s (%s)</strong>.</p>
                          <p>Your test is scheduled on:</p>
                          <ul>
                            <li><strong>Date:</strong> %s</li>
                            <li><strong>Time:</strong> %s</li>
                          </ul>
                          <p style='text-align: center; margin: 25px 0;'>
                            <a href="%s" style="background-color: #28a745; color: white; padding: 12px 25px; 
                               text-decoration: none; border-radius: 5px; display: inline-block; font-weight: bold;">
                              Acknowledge
                            </a>
                          </p>
                          <p style='margin-top: 30px;'>Regards,<br/><strong>RightPath HR Team</strong></p>
                        </div>
                      </body>
                    </html>
                    """,
                    getLogoHtml(),
                    params.get("firstName"),
                    params.get("lastName"),
                    params.get("jobTitle"),
                    params.get("jobPrefix"),
                    params.get("examDate"),
                    params.get("examTime"),
                    params.get("acknowledgeUrl")
                );
                break;

            case ACKNOWLEDGEMENT_CONFIRMATION:
                subject = "Acknowledgement Received - RightPath";
                htmlBody = String.format("""
                    <html>
                      <body style='font-family: Arial, sans-serif; background-color: #f9f9f9; padding: 20px;'>
                        <div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); padding: 30px;'>
                          %s
                          <h2 style='color: #2E86C1;'>Thank You for Confirming!</h2>
                          <p>Hi <strong>%s %s</strong>,</p>
                          <p>We have received your acknowledgement for the <strong>%s</strong> position.</p>
                          <p>Your test details:</p>
                          <ul>
                            <li><strong>Date:</strong> %s</li>
                            <li><strong>Time:</strong> %s</li>
                          </ul>
                          <p>Please arrive 15 minutes early at our office.</p>
                          <hr style='border: 0; height: 1px; background: #e0e0e0; margin: 20px 0;' />
                          <p style='margin-top: 30px;'>Regards,<br/><strong>RightPath Team</strong></p>
                        </div>
                      </body>
                    </html>
                    """,
                    getLogoHtml(),
                    params.get("firstName"),
                    params.get("lastName"),
                    params.get("jobTitle"),
                    params.get("examDate"),
                    params.get("examTime")
                );
                break;

            case RECONFIRMATION:
                subject = "Reminder: Upcoming Test - RightPath";
                htmlBody = String.format("""
                    <html>
                      <body style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>
                        <div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); padding: 30px;'>
                          %s
                          <h2 style='color: #d35400;'>Test Day Reminder</h2>
                          <p>Hi <strong>%s %s</strong>,</p>
                          <p>This is a reminder for your upcoming test for <strong>%s (%s)</strong>:</p>
                          <ul>
                            <li><strong>Date:</strong> %s</li>
                            <li><strong>Time:</strong> %s</li>
                            <li><strong>Venue:</strong> RightPath Office, Narsingi, Hyderabad</li>
                          </ul>
                          <p>Please bring:</p>
                          <ol>
                            <li>Printed copy of this email</li>
                            <li>Government-issued ID proof</li>
                            <li>Your own stationery</li>
                          </ol>
                          <hr style='border: 0; height: 1px; background: #e0e0e0; margin: 20px 0;' />
                          <p style='margin-top: 30px;'>Regards,<br/><strong>RightPath HR Team</strong></p>
                        </div>
                      </body>
                    </html>
                    """,
                    getLogoHtml(),
                    params.get("firstName"),
                    params.get("lastName"),
                    params.get("jobTitle"),
                    params.get("jobPrefix"),
                    params.get("examDate"),
                    params.get("examTime")
                );
                break;

            case REJECTION:
                subject = "Regarding Your Application - RightPath";
                htmlBody = String.format("""
                    <html>
                      <body style='font-family: Arial, sans-serif; background-color: #f9f9f9; padding: 20px;'>
                        <div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); padding: 30px;'>
                          %s
                          <h2 style='color: #c0392b;'>Application Update</h2>
                          <p>Dear <strong>%s %s</strong>,</p>
                          <p>Thank you for applying for the <strong>%s</strong> position at RightPath.</p>
                          <p>After careful consideration, we regret to inform you that your application was not successful at this time.</p>
                          <p>We appreciate your interest in our company and encourage you to apply for future opportunities.</p>
                          <hr style='border: 0; height: 1px; background: #e0e0e0; margin: 20px 0;' />
                          <p style='margin-top: 30px;'>Best regards,<br/><strong>RightPath Recruitment Team</strong></p>
                        </div>
                      </body>
                    </html>
                    """,
                    getLogoHtml(),
                    params.get("firstName"),
                    params.get("lastName"),
                    params.get("jobTitle")
                );
                break;

            case WRITTEN_TEST_SUCCESS:
                subject = "Congratulations! Next Steps - RightPath";
                htmlBody = String.format("""
                    <html>
                      <body style='font-family: Arial, sans-serif; background-color: #f9f9f9; padding: 20px;'>
                        <div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); padding: 30px;'>
                          %s
                          <h2 style='color: #27ae60;'>Congratulations!</h2>
                          <p>Dear <strong>%s %s</strong>,</p>
                          <p>We are pleased to inform you that you have successfully cleared the written test for <strong>%s</strong>.</p>
                          <p>Our HR team will contact you shortly to schedule the next steps in the hiring process.</p>
                          <p>Please keep an eye on your email for further communications.</p>
                          <hr style='border: 0; height: 1px; background: #e0e0e0; margin: 20px 0;' />
                          <p style='margin-top: 30px;'>Best regards,<br/><strong>RightPath Hiring Team</strong></p>
                        </div>
                      </body>
                    </html>
                    """,
                    getLogoHtml(),
                    params.get("firstName"),
                    params.get("lastName"),
                    params.get("jobTitle")
                );
                break;

            case WRITTEN_TEST_FAILURE:
                subject = "Your Application Status - RightPath";
                htmlBody = String.format("""
                    <html>
                      <body style='font-family: Arial, sans-serif; background-color: #f9f9f9; padding: 20px;'>
                        <div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); padding: 30px;'>
                          %s
                          <h2 style='color: #7f8c8d;'>Application Update</h2>
                          <p>Dear <strong>%s %s</strong>,</p>
                          <p>Thank you for taking the written test for <strong>%s</strong>.</p>
                          <p>Unfortunately you have not qualified for the next round.We wish you better luck in future.</p>
                          <hr style='border: 0; height: 1px; background: #e0e0e0; margin: 20px 0;' />
                          <p style='margin-top: 30px;'>Best regards,<br/><strong>RightPath Team</strong></p>
                        </div>
                      </body>
                    </html>
                    """,
                    getLogoHtml(),
                    params.get("firstName"),
                    params.get("lastName"),
                    params.get("jobTitle")
                );
                break;
            
            default:
                throw new IllegalArgumentException("Unsupported email type: " + emailType);
        }

        sendHtmlEmail(recipientEmail, subject, htmlBody);
        
        // Send WhatsApp notification if applicable
        sendWhatsAppNotification(emailType, params);
    }


    @Override
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                message,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.addInline(LOGO_CID, new ClassPathResource("static/images/IsignLogo-removebg-preview.png"), "image/png");

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private String getLogoHtml() {
        return String.format("""
            <div style='text-align:center;margin-bottom:20px'>
              <img src='cid:%s' alt='RightPath Logo' style='height:60px'/>
            </div>
            """, LOGO_CID);
    }
}