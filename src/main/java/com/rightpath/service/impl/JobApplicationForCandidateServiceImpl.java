package com.rightpath.service.impl;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.rightpath.dto.JobApplicationForCandidateDTO;
import com.rightpath.entity.JobApplicationForCandidate;
import com.rightpath.entity.JobPost;
import com.rightpath.entity.Users;
import com.rightpath.enums.ApplicationStatus;
import com.rightpath.enums.EmailType;
import com.rightpath.exceptions.ApplicationDeadlinePassedException;
import com.rightpath.exceptions.ResourceNotFoundException;
import com.rightpath.repository.JobApplicationForCandidateRepository;
import com.rightpath.repository.JobPostRepository;
import com.rightpath.repository.UsersRepository;
import com.rightpath.service.JobApplicationForCandidateService;
import com.rightpath.service.ResumeService;
import com.rightpath.service.WhatsAppService;
import com.rightpath.util.StatusTransitionValidator;
import com.rightpath.util.SynonymLoader;

import jakarta.transaction.Transactional;



@Service
public class JobApplicationForCandidateServiceImpl implements JobApplicationForCandidateService {

    @Autowired
    private JobApplicationForCandidateRepository applicationForCandidateRepository;

    @Autowired
    private UsersRepository usersRepository;
    
    @Autowired
    private ResumeService resumeService;
    
    @Autowired
    private final JobPostRepository jobPostRepository;
    
    @Autowired
    private SynonymLoader synonymLoader;
    
    @Autowired
    private EmailServiceImpl emailService;
    
    @Autowired
    private WhatsAppService whatsAppService;
    

    private final String baseUrl;

    @Value("${ats.screening.threshold:60.0}")
    private double atsThreshold;



    @Autowired
    private JavaMailSender mailSender;

    public JobApplicationForCandidateServiceImpl(JobApplicationForCandidateRepository applicationForCandidateRepository,
			UsersRepository usersRepository, JobPostRepository jobPostRepository,@Value("${app.base.url}")  String baseUrl) {
		super();
		this.applicationForCandidateRepository = applicationForCandidateRepository;
		this.usersRepository = usersRepository;
		this.jobPostRepository = jobPostRepository;
		this.baseUrl=baseUrl;
	}

    
    private String getLogoHtml() {
        return """
            <div style='text-align: center; margin-bottom: 20px;'>
                <img src='cid:isigntech-logo' alt='Company Logo' style='height: 60px;'/>
            </div>
            """;
    }
    
    @Override
    public void applyForJob(JobApplicationForCandidateDTO dto) {
        Users user = usersRepository.findById(dto.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + dto.getEmail()));

        JobPost jobPost = jobPostRepository.findByJobPrefix(dto.getJobPrefix())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + dto.getJobPrefix()));

        if (jobPost.getApplicationDeadline() != null
                && LocalDate.now().isAfter(jobPost.getApplicationDeadline())) {
            throw new ApplicationDeadlinePassedException(
                    "Application deadline for " + jobPost.getJobTitle() + " has passed");
        }

        String jobTitle = jobPost.getJobTitle();

        JobApplicationForCandidate application = JobApplicationForCandidate.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .experience(dto.getExperience())
                .address(dto.getAddress())
                .jobRole(dto.getJobRole())
                .mobileNumber(dto.getMobileNumber())
                .user(user)
                .jobPost(jobPost) // set relationship with JobPost
                .build();

        try {
            if (dto.getResume() != null && !dto.getResume().isEmpty()) {
                application.setResumeFileName(dto.getResume().getOriginalFilename());
                application.setResumeData(dto.getResume().getBytes());
                application.setContentType(dto.getResume().getContentType());
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading resume file", e);
        }

        // Centralized status logic: REF-000 → SHORTLISTED, others → APPLIED
        if ("REF-000".equalsIgnoreCase(dto.getJobPrefix())) {
            application.setStatus(ApplicationStatus.SHORTLISTED);
        } else {
            application.setStatus(ApplicationStatus.APPLIED);
        }

        applicationForCandidateRepository.save(application);

        // Prepare params for universal email
        Map<String, Object> emailParams = new HashMap<>();
        emailParams.put("recipientEmail", dto.getEmail());
        emailParams.put("firstName", dto.getFirstName());
        emailParams.put("lastName", dto.getLastName());
        emailParams.put("jobTitle", jobTitle);
        emailParams.put("jobPrefix", dto.getJobPrefix());
        emailParams.put("mobileNumber", dto.getMobileNumber());

        emailService.sendUniversalEmail(EmailType.APPLICATION_SUCCESS, emailParams);

        whatsAppService.sendWhatsAppMessage(
                dto.getMobileNumber(),
                WhatsAppService.MessageType.JOB_APPLIED,
                dto.getFirstName(),
                dto.getLastName(),
                dto.getJobPrefix(),
                jobTitle
        );
    }





    
    @Override
    public List<JobApplicationForCandidateDTO> getAllApplications() {
        List<JobApplicationForCandidate> applications = applicationForCandidateRepository.findAll();
        return applications.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public List<JobApplicationForCandidateDTO> getApplicationsByEmail(String email) {
        List<JobApplicationForCandidate> applications = applicationForCandidateRepository.findByUserEmail(email);
        return applications.stream().map(this::convertToDTO).collect(Collectors.toList());
    }



  
   @Transactional
public void updateJobApplicationByJobPrefixAndEmail(JobApplicationForCandidateDTO dto) {
    List<JobApplicationForCandidate> applications = 
        applicationForCandidateRepository.findByJobPrefixAndEmail(dto.getJobPrefix(), dto.getEmail());

    if (applications.isEmpty()) {
        throw new RuntimeException("No application found for the given jobPrefix and email.");
    }

    JobApplicationForCandidate application = applications.get(0);

    // ✅ Update only if not null
    if (dto.getFirstName() != null) application.setFirstName(dto.getFirstName());
    if (dto.getLastName() != null) application.setLastName(dto.getLastName());
    if (dto.getExperience() != null) application.setExperience(dto.getExperience());
    if (dto.getAddress() != null) application.setAddress(dto.getAddress());
    if (dto.getJobRole() != null) application.setJobRole(dto.getJobRole());
    if (dto.getMobileNumber() != null) application.setMobileNumber(dto.getMobileNumber());
    
    
    // ✅ Update resume if new file is uploaded
    if (dto.getResume() != null && !dto.getResume().isEmpty()) {
        application.setResumeFileName(dto.getResume().getOriginalFilename());
        application.setContentType(dto.getResume().getContentType());
        try {
            application.setResumeData(dto.getResume().getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Resume processing failed", e);
        }
    }

    // ✅ Ensure save is called
    applicationForCandidateRepository.save(application);
}

 
    
    private JobApplicationForCandidateDTO convertToDTO(JobApplicationForCandidate app) {
        JobApplicationForCandidateDTO dto = new JobApplicationForCandidateDTO();
        dto.setId(app.getId());
        dto.setFirstName(app.getFirstName());
        dto.setLastName(app.getLastName());
        dto.setExperience(app.getExperience());
        dto.setAddress(app.getAddress());
        dto.setJobRole(app.getJobRole());
        dto.setResumeFileName(app.getResumeFileName());
        dto.setContentType(app.getContentType());
        dto.setStatus(app.getStatus() != null ? app.getStatus().name() : null);
        dto.setConfirmationStatus(app.getConfirmationStatus());
        dto.setAcknowledgedStatus(app.getAcknowledgedStatus());
        dto.setReconfirmationStatus(app.getReconfirmationStatus());
        dto.setExamLinkStatus(app.getExamLinkStatus());
        dto.setExamCompletedStatus(app.getExamCompletedStatus());
        dto.setUserEmail(app.getUser().getEmail());
        dto.setEmail(app.getUser().getEmail());
        dto.setMobileNumber(app.getMobileNumber());
        dto.setRejectionStatus(app.getRejectionStatus());
        dto.setWrittenTestStatus(app.getWrittenTestStatus());
        dto.setInterview(app.getInterview());
        if (app.getJobPost() != null) {
            dto.setJobPrefix(app.getJobPost().getJobPrefix());
            dto.setJobTitle(app.getJobPost().getJobTitle());
            dto.setCompanyName(app.getJobPost().getCompanyName());
            dto.setApplicationDeadline(app.getJobPost().getApplicationDeadline() != null
                    ? app.getJobPost().getApplicationDeadline().toString() : null);
        } else {
            dto.setJobPrefix(null);
        }

        return dto;
    }
    

    @Override
    public List<JobApplicationForCandidateDTO> getApplicationsByJobPrefix(String jobPrefix) {
        List<JobApplicationForCandidate> applications = applicationForCandidateRepository.findByJobPrefix(jobPrefix);
        return applications.stream().map(this::convertToDTO).collect(Collectors.toList());
    }
    
    
    @Override
    public List<JobApplicationForCandidateDTO> getApplicantsByJobPostId(Long jobPostId) {
        JobPost jobPost = jobPostRepository.findById(jobPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobPostId));

        List<JobApplicationForCandidate> applications = applicationForCandidateRepository.findByJobPost(jobPost);

        return applications.stream()
                .map(app -> {
                    JobApplicationForCandidateDTO dto = new JobApplicationForCandidateDTO();
                    dto.setId(app.getId());
                    dto.setUserEmail(app.getUser().getEmail());
                    dto.setJobPrefix(app.getJobPost().getJobPrefix());
                    return dto;
                })
                .collect(Collectors.toList());
    }
    
    
    /**
     * CORE LOGIC: Centralized Filtering Logic
     */
    private double calculateMatchPercent(JobApplicationForCandidate app, String[] requiredSkills, Map<String, List<String>> synonyms) {
        String resumeText = resumeService.extractText(app.getResumeData()).toLowerCase();

        long matchCount = Arrays.stream(requiredSkills)
                .filter(skill -> {
                    List<String> variations = synonyms.getOrDefault(skill, List.of(skill));
                    return variations.stream().anyMatch(resumeText::contains);
                })
                .count();

        return (matchCount * 100.0) / requiredSkills.length;
    }
    
    
    
    /**
     * MAIN METHOD TO PROCESS ALL CANDIDATES
     */
    @Override
    public List<JobApplicationForCandidateDTO> filterCandidatesByPrefix(String jobPrefix) {
        JobPost jobPost = jobPostRepository.findByJobPrefix(jobPrefix)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with prefix: " + jobPrefix));

        String[] requiredSkills = jobPost.getKeySkills().toLowerCase().split(",\\s*");
        Map<String, List<String>> synonyms = synonymLoader.getSynonymMap();

        List<JobApplicationForCandidate> applicants = applicationForCandidateRepository.findByJobPost(jobPost);

        List<JobApplicationForCandidateDTO> processedList = new ArrayList<>();

        for (JobApplicationForCandidate app : applicants) {
            double matchPercent = calculateMatchPercent(app, requiredSkills, synonyms);

            // Only run ATS screening on candidates still in APPLIED status
            if (app.getStatus() == ApplicationStatus.APPLIED) {
                ApplicationStatus finalStatus = (matchPercent >= atsThreshold) ? ApplicationStatus.SHORTLISTED : ApplicationStatus.REJECTED;
                StatusTransitionValidator.validate(app.getStatus(), finalStatus);
                app.setStatus(finalStatus);
                applicationForCandidateRepository.save(app);
            }

            JobApplicationForCandidateDTO dto = convertToDTO(app);
            dto.setMatchPercent(matchPercent);
            processedList.add(dto);
        }
        return processedList;
    }

    @Override
    public List<JobApplicationForCandidateDTO> getShortlistedCandidatesByPrefix(String jobPrefix) {
        return filterCandidatesByPrefix(jobPrefix).stream()
                .filter(dto -> "SHORTLISTED".equalsIgnoreCase(dto.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public List<JobApplicationForCandidateDTO> getRejectedCandidatesByPrefix(String jobPrefix) {
        return filterCandidatesByPrefix(jobPrefix).stream()
                .filter(dto -> "REJECTED".equalsIgnoreCase(dto.getStatus()))
                .collect(Collectors.toList());
    }

    public JobApplicationForCandidateDTO getApplicationByJobPrefixAndEmail(String jobPrefix, String email) {
        List<JobApplicationForCandidate> apps = applicationForCandidateRepository.findByJobPrefixAndEmail(jobPrefix, email);

        if (apps.isEmpty()) throw new RuntimeException("No application found");

        return convertToDTO(apps.get(0));
    }

     
	    public List<JobApplicationForCandidateDTO> filterCandidates(Long jobPostId) {
	        JobPost jobPost = jobPostRepository.findById(jobPostId)
	                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
	
	        String[] requiredSkills = jobPost.getKeySkills().toLowerCase().split(",\\s*");
	
	        List<JobApplicationForCandidate> applicants = applicationForCandidateRepository.findByJobPost(jobPost);
	
	        Map<String, List<String>> synonyms = synonymLoader.getSynonymMap();
	
	        return applicants.stream()
	                .filter(app -> {
	                    String resumeText = resumeService.extractText(app.getResumeData()).toLowerCase();
	
	                    long matchCount = Arrays.stream(requiredSkills)
	                            .filter(skill -> {
	                                List<String> variations = synonyms.getOrDefault(skill, List.of(skill));
	                                return variations.stream().anyMatch(resumeText::contains);
	                            })
	                            .count();
	
	                    double matchPercent = (matchCount * 100.0) / requiredSkills.length;
	                    System.out.println("Resume Text: " + resumeText);
	                    System.out.println("Required Skills: " + Arrays.toString(requiredSkills));
	                    System.out.println("Match %: " + matchPercent);
	                    return matchPercent >= atsThreshold;
	                })
	                .map(this::convertToDTO)
	                .collect(Collectors.toList());
	    }

    
    
    
	    @Override
	    public JobApplicationForCandidate getById(Long id) {
	        return applicationForCandidateRepository.findById(id)
	                         .orElseThrow(() -> new RuntimeException("Candidate not found with id: " + id));
	    }
    
	    @Override
	    public void sendAcknowledgementMailAndUpdateStatus(String jobPrefix, String email, String date, String time) {
	        List<JobApplicationForCandidate> applications = applicationForCandidateRepository
	                .findByJobPrefixAndEmail(jobPrefix, email);
	        JobPost jobs = jobPostRepository.findByJobPrefix(jobPrefix)
	                .orElseThrow(() -> new RuntimeException("No job found for prefix: " + jobPrefix));

	        if (applications.isEmpty()) {
	            throw new RuntimeException("No application found for the given job prefix and email.");
	        }

	        if (date == null || date.isBlank() || time == null || time.isBlank()) {
	            throw new RuntimeException("Date and time are required for acknowledgement mail.");
	        }

	        JobApplicationForCandidate application = applications.get(0);

	        // Validate workflow transition: SHORTLISTED → ACKNOWLEDGED
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.ACKNOWLEDGED);

	        try {
	            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

	            LocalDate examDate = LocalDate.parse(date.trim(), dateFormatter);
	            LocalTime examTime = LocalTime.parse(time.trim(), timeFormatter);

	            application.setStatus(ApplicationStatus.ACKNOWLEDGED);
	            application.setConfirmationStatus("Confirmation Sent");
	            application.setExamDate(examDate);
	            application.setExamTime(examTime);
	            applicationForCandidateRepository.save(application);

	            String acknowledgeUrl = baseUrl +"/api/job-applications/acknowledge?jobPrefix="
	                    + jobPrefix + "&email=" + email;

	            // Prepare params for universal email
	            Map<String, Object> emailParams = new HashMap<>();
	            emailParams.put("recipientEmail", email);
	            emailParams.put("firstName", application.getFirstName());
	            emailParams.put("lastName", application.getLastName());
	            emailParams.put("jobTitle", jobs.getJobTitle());
	            emailParams.put("jobPrefix", jobPrefix);
	            emailParams.put("examDate", date);
	            emailParams.put("examTime", time);
	            emailParams.put("acknowledgeUrl", acknowledgeUrl);
	            emailParams.put("mobileNumber", application.getMobileNumber());

	            emailService.sendUniversalEmail(EmailType.ACKNOWLEDGEMENT, emailParams);

	            whatsAppService.sendWhatsAppMessage(
	                    application.getMobileNumber(),
	                    WhatsAppService.MessageType.EXAM_SCHEDULE,
	                    LocalDateTime.of(application.getExamDate(), application.getExamTime()),
	                    LocalDateTime.of(application.getExamDate(), application.getExamTime().plusHours(1))
	                );

	        } catch (DateTimeParseException e) {
	            throw new RuntimeException("Failed to parse date or time: " + e.getMessage());
	        } catch (Exception e) {
	            throw new RuntimeException("Error during acknowledgement process: " + e.getMessage());
	        }
	    }


	    
	    @Override
	    public String acknowledgeCandidate(String jobPrefix, String email) {
	        List<JobApplicationForCandidate> applications =
	                applicationForCandidateRepository.findByJobPrefixAndEmail(jobPrefix, email);

	        if (applications.isEmpty()) {
	            throw new RuntimeException(
	                "No matching application found for the provided jobPrefix and email.");
	        }

	        JobApplicationForCandidate application = applications.get(0);

	        if (!"Confirmation Sent".equalsIgnoreCase(application.getConfirmationStatus())) {
	            return "Acknowledgement not allowed. Current status: "
	                   + application.getConfirmationStatus();
	        }

	        if (application.getStatus() != ApplicationStatus.ACKNOWLEDGED) {
	            return "Cannot acknowledge. Current status: " + application.getStatus();
	        }

	        // Validate workflow transition: ACKNOWLEDGED → ACKNOWLEDGED_BACK
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.ACKNOWLEDGED_BACK);
	        application.setStatus(ApplicationStatus.ACKNOWLEDGED_BACK);
	        application.setAcknowledgedStatus("Acknowledged Back");
	        applicationForCandidateRepository.save(application);
	        
	        // Prepare params for universal email
	        Map<String, Object> emailParams = new HashMap<>();
	        emailParams.put("recipientEmail", email);
	        emailParams.put("firstName", application.getFirstName());
	        emailParams.put("lastName", application.getLastName());
	        emailParams.put("jobTitle", application.getJobPost().getJobTitle());
	        emailParams.put("examDate", application.getExamDate().toString());
	        emailParams.put("examTime", application.getExamTime().toString());
	        emailParams.put("mobileNumber", application.getMobileNumber());
	        
	        emailService.sendUniversalEmail(EmailType.ACKNOWLEDGEMENT_CONFIRMATION, emailParams);

	        // WhatsApp notification remains the same
	        whatsAppService.sendWhatsAppMessage(
	                application.getMobileNumber(),
	                WhatsAppService.MessageType.ACKNOWLEDGED,
	                LocalDateTime.of(application.getExamDate(), application.getExamTime()),
	                LocalDateTime.of(application.getExamDate(), application.getExamTime().plusHours(1))
	            );

	        return "Acknowledgement received successfully.";
	    }

	    @Override
	    public void sendReconfirmationMail(String jobPrefix, String email) {
	        List<JobApplicationForCandidate> applications = applicationForCandidateRepository
	                .findByJobPrefixAndEmail(jobPrefix, email);

	        if (applications.isEmpty()) {
	            throw new RuntimeException("No application found for the given job prefix and email.");
	        }

	        JobApplicationForCandidate application = applications.get(0);

	        // Validate workflow transition: ACKNOWLEDGED_BACK → RECONFIRMED
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.RECONFIRMED);

	        if (application.getExamDate() == null || application.getExamTime() == null) {
	            throw new RuntimeException("Exam date or time not found. Please send acknowledgement mail first.");
	        }

	        application.setStatus(ApplicationStatus.RECONFIRMED);
	        application.setReconfirmationStatus("Re-confirmation Mail Sent");
	        applicationForCandidateRepository.save(application);

	        // Prepare params for universal email
	        Map<String, Object> emailParams = new HashMap<>();
	        emailParams.put("recipientEmail", email);
	        emailParams.put("firstName", application.getFirstName());
	        emailParams.put("lastName", application.getLastName());
	        emailParams.put("jobTitle", application.getJobPost().getJobTitle());
	        emailParams.put("jobPrefix", jobPrefix);
	        emailParams.put("examDate", application.getExamDate().toString());
	        emailParams.put("examTime", application.getExamTime().toString());
	        emailParams.put("mobileNumber", application.getMobileNumber());

	        emailService.sendUniversalEmail(EmailType.RECONFIRMATION, emailParams);

	        whatsAppService.sendWhatsAppMessage(
	                application.getMobileNumber(),
	                WhatsAppService.MessageType.RECONFIRMATION,
	                LocalDateTime.of(application.getExamDate(), application.getExamTime()),
	                LocalDateTime.of(application.getExamDate(), application.getExamTime().plusHours(1))
	            );
	    }
	    
	    @Override
	    public void sendRejectionMail(String jobPrefix, String email) {
	        List<JobApplicationForCandidate> applications =
	                applicationForCandidateRepository.findByJobPrefixAndEmail(jobPrefix, email);

	        if (applications.isEmpty()) {
	            throw new RuntimeException("No application found for the given job prefix and email.");
	        }

	        JobApplicationForCandidate application = applications.get(0);

	        // Validate workflow transition: only certain stages allow rejection
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.REJECTED);

	        application.setStatus(ApplicationStatus.REJECTED);
	        application.setRejectionStatus("Rejection Mail Sent");
	        applicationForCandidateRepository.save(application);

	        // Prepare params for universal email
	        Map<String, Object> emailParams = new HashMap<>();
	        emailParams.put("recipientEmail", email);
	        emailParams.put("firstName", application.getFirstName());
	        emailParams.put("lastName", application.getLastName());
	        emailParams.put("jobTitle", application.getJobPost().getJobTitle());
	        emailParams.put("mobileNumber", application.getMobileNumber());

	        emailService.sendUniversalEmail(EmailType.REJECTION, emailParams);

	        whatsAppService.sendWhatsAppMessage(
	                application.getMobileNumber(),
	                WhatsAppService.MessageType.REJECTION,
	                application.getFirstName() + " " + application.getLastName()
	            );
	    }

	    
	    

	    @Override
	    public void updateWrittenTestStatus(String jobPrefix, String email, boolean isAptitudePassed, boolean isProgrammingPassed) {
	        List<JobApplicationForCandidate> applications = applicationForCandidateRepository.findByJobPrefixAndEmail(jobPrefix, email);

	        if (applications.isEmpty()) {
	            throw new RuntimeException("No job application found for the given job prefix and email");
	        }

	        String status = (isAptitudePassed && isProgrammingPassed) ? "Passed" : "Failed";

	        for (JobApplicationForCandidate application : applications) {
	            application.setWrittenTestStatus(status);
	            applicationForCandidateRepository.save(application);
	        }
	    }
	    
	    @Override
	    public void sendSuccessMail(String jobPrefix, String email) {
	        List<JobApplicationForCandidate> applications = applicationForCandidateRepository.findByJobPrefixAndEmail(jobPrefix, email);
	        if (applications.isEmpty()) {
	            throw new RuntimeException("No application found for the given job prefix and email.");
	        }

	        JobApplicationForCandidate application = applications.get(0);

	        // Validate workflow transition: INTERVIEW_COMPLETED → SELECTED
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.SELECTED);

	        application.setStatus(ApplicationStatus.SELECTED);
	        applicationForCandidateRepository.save(application);

	        // Prepare params for universal email
	        Map<String, Object> emailParams = new HashMap<>();
	        emailParams.put("recipientEmail", email);
	        emailParams.put("firstName", application.getFirstName());
	        emailParams.put("lastName", application.getLastName());
	        emailParams.put("jobTitle", application.getJobPost().getJobTitle());
	        emailParams.put("mobileNumber", application.getMobileNumber());

	        emailService.sendUniversalEmail(EmailType.WRITTEN_TEST_SUCCESS, emailParams);

	        whatsAppService.sendWhatsAppMessage(
	                application.getMobileNumber(),
	                WhatsAppService.MessageType.SHORTLIST,
	                application.getFirstName() + " " + application.getLastName()
	            );
	    }

	    @Override
	    public void sendFailureMail(String jobPrefix, String email) {
	        List<JobApplicationForCandidate> applications = applicationForCandidateRepository.findByJobPrefixAndEmail(jobPrefix, email);
	        if (applications.isEmpty()) {
	            throw new RuntimeException("No application found for the given job prefix and email.");
	        }

	        JobApplicationForCandidate application = applications.get(0);

	        // Validate workflow transition: reject from allowed stages
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.REJECTED);

	        application.setStatus(ApplicationStatus.REJECTED);
	        application.setRejectionStatus("Failed - Rejection Mail Sent");
	        applicationForCandidateRepository.save(application);

	        // Prepare params for universal email
	        Map<String, Object> emailParams = new HashMap<>();
	        emailParams.put("recipientEmail", email);
	        emailParams.put("firstName", application.getFirstName());
	        emailParams.put("lastName", application.getLastName());
	        emailParams.put("jobTitle", application.getJobPost().getJobTitle());
	        emailParams.put("mobileNumber", application.getMobileNumber());

	        emailService.sendUniversalEmail(EmailType.WRITTEN_TEST_FAILURE, emailParams);

	        whatsAppService.sendWhatsAppMessage(
	                application.getMobileNumber(),
	                WhatsAppService.MessageType.TestFailed
	            );
	    }
	    
	    

	    @Override
	    public void sendExamLink(String jobPrefix, String email, String dateTime) {
	        List<JobApplicationForCandidate> applications = applicationForCandidateRepository
	                .findByJobPrefixAndEmail(jobPrefix, email);

	        if (applications.isEmpty()) {
	            throw new RuntimeException("No application found for the given job prefix and email.");
	        }

	        JobApplicationForCandidate application = applications.get(0);

	        // Validate workflow transition: RECONFIRMED → EXAM_SENT
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.EXAM_SENT);

	        LocalDateTime startTime;
	        if (dateTime != null && !dateTime.isEmpty()) {
	            startTime = LocalDateTime.parse(dateTime);
	        } else {
	            startTime = LocalDateTime.now();
	        }
	        LocalDateTime endTime = startTime.plusHours(1);

	        application.setStatus(ApplicationStatus.EXAM_SENT);
	        application.setExamLinkStatus("Exam Link Sent");
	        applicationForCandidateRepository.save(application);

	        emailService.sendExamLink(email, startTime, endTime, jobPrefix);

	        whatsAppService.sendWhatsAppMessage(
	                application.getMobileNumber(),
	                WhatsAppService.MessageType.EXAM_SCHEDULE,
	                startTime,
	                endTime
	        );
	    }

	    //method to schedule interview
	    public JobApplicationForCandidate scheduleInterview(String jobPrefix, String email) {

	        JobApplicationForCandidate application = applicationForCandidateRepository
	                .findByJobPost_JobPrefixAndUser_Email(jobPrefix, email)
	                .orElseThrow(() -> new RuntimeException("Application not found"));

	        // Validate workflow transition: EXAM_COMPLETED → INTERVIEW_SCHEDULED
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.INTERVIEW_SCHEDULED);

	        application.setStatus(ApplicationStatus.INTERVIEW_SCHEDULED);
	        application.setInterview("Scheduled");

	        return applicationForCandidateRepository.save(application);
	    }


    
}