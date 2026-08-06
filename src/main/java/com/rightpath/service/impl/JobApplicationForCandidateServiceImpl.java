package com.rightpath.service.impl;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.rightpath.enums.ReferralStatus;
import com.rightpath.exceptions.ApplicationDeadlinePassedException;
import com.rightpath.exceptions.JobPostNotFoundException;
import com.rightpath.exceptions.ResourceNotFoundException;
import com.rightpath.repository.AssessmentRepository;
import com.rightpath.repository.JobApplicationForCandidateRepository;
import com.rightpath.repository.JobPostRepository;
import com.rightpath.repository.UsersRepository;
import com.rightpath.service.JobApplicationForCandidateService;
import com.rightpath.service.ResumeService;
import com.rightpath.service.WhatsAppService;
import com.rightpath.util.BusinessSchedule;
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

    @Autowired
    private AssessmentRepository assessmentRepository;

    /** Owns the business timezone: "now" for validation, and display formatting. */
    @Autowired
    private BusinessSchedule businessSchedule;

    private static final Logger logger = LoggerFactory.getLogger(JobApplicationForCandidateServiceImpl.class);


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

        // An archived posting reads as gone to candidates: the public apply link must
        // answer with a clean 404, not let someone apply to a deleted job.
        JobPost jobPost = jobPostRepository.findByJobPrefix(dto.getJobPrefix())
                .filter(post -> post.getDeletedAt() == null)
                .orElseThrow(() -> new JobPostNotFoundException(dto.getJobPrefix()));

        // Business date, so this agrees with the ACTIVE/EXPIRED buckets the job
        // listing shows; on a UTC server LocalDate.now() would close applications
        // for a job the candidate can still see as active (and vice versa).
        if (jobPost.getApplicationDeadline() != null
                && businessSchedule.today().isAfter(jobPost.getApplicationDeadline())) {
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
                .referralId(dto.getReferralId())
                .referralName(dto.getReferralName())
                .user(user)
                .jobPost(jobPost) // set relationship with JobPost
                .build();

        // Default referral status to PENDING only when the candidate actually
        // supplied referral details; a non-referred application has no referral status.
        boolean hasReferral = (dto.getReferralId() != null && !dto.getReferralId().isBlank())
                || (dto.getReferralName() != null && !dto.getReferralName().isBlank());
        if (hasReferral) {
            application.setReferralStatus(ReferralStatus.PENDING);
        }

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
        throw new ResourceNotFoundException("No application found for the given jobPrefix and email.");
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
        dto.setReferralId(app.getReferralId());
        dto.setReferralName(app.getReferralName());
        dto.setReferralStatus(app.getReferralStatus() != null ? app.getReferralStatus().name() : null);
        dto.setRejectionStatus(app.getRejectionStatus());
        dto.setWrittenTestStatus(app.getWrittenTestStatus());
        dto.setInterview(app.getInterview());
        dto.setCurrentStage(JobApplicationForCandidateDTO.humanizeStage(dto.getStatus()));
        dto.setAtsScanStatus(JobApplicationForCandidateDTO.deriveAtsScanStatus(app.getAtsScanStatus(), dto.getStatus()));
        dto.setShortlistStatus(JobApplicationForCandidateDTO.deriveShortlistStatus(app.getShortlistStatus(), dto.getStatus()));
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

            // Recompute and persist the shortlist decision on EVERY run so the
            // status matches the current score (e.g. after a resume edit + re-screen).
            // Only candidates in the ATS screening phase are (re)evaluated; see
            // isAtsScreenable for what is intentionally left untouched.
            if (isAtsScreenable(app)) {
                boolean shortlisted = matchPercent >= atsThreshold;
                // Set directly rather than via StatusTransitionValidator: a re-screen is
                // an in-phase re-evaluation (REJECTED->SHORTLISTED, or same->same), which
                // the forward-only pipeline validator intentionally forbids.
                app.setStatus(shortlisted ? ApplicationStatus.SHORTLISTED : ApplicationStatus.REJECTED);
                // Record the ATS scan and shortlist outcomes in their own columns.
                app.setAtsScanStatus("Screening Completed");
                app.setShortlistStatus(shortlisted ? "Shortlisted" : "Not Shortlisted");
                applicationForCandidateRepository.save(app);
            }

            JobApplicationForCandidateDTO dto = convertToDTO(app);
            dto.setMatchPercent(matchPercent);
            processedList.add(dto);
        }
        return processedList;
    }

    /**
     * Whether an application may be (re)evaluated by ATS screening on this run.
     *
     * <ul>
     *   <li>APPLIED — the first screen.</li>
     *   <li>SHORTLISTED / REJECTED that ATS itself produced (atsScanStatus =
     *       "Screening Completed") and that was not subsequently closed by a
     *       manual or written-test rejection (rejectionStatus set) — these may be
     *       re-screened, e.g. after a resume edit, so the decision tracks the new score.</li>
     * </ul>
     *
     * Everything else is left untouched: candidates who have progressed past
     * shortlisting (ACKNOWLEDGED onward) must not be reverted, referral
     * auto-shortlists (never ATS-screened) keep their status, and manually/test-
     * rejected candidates are not resurrected.
     */
    private boolean isAtsScreenable(JobApplicationForCandidate app) {
        ApplicationStatus status = app.getStatus();

        if (status == ApplicationStatus.APPLIED) {
            return true;
        }

        boolean previouslyScreened = "Screening Completed".equals(app.getAtsScanStatus());
        boolean inScreeningPhase = status == ApplicationStatus.SHORTLISTED || status == ApplicationStatus.REJECTED;
        boolean manuallyClosed = app.getRejectionStatus() != null && !app.getRejectionStatus().isBlank();

        return previouslyScreened && inScreeningPhase && !manuallyClosed;
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

        if (apps.isEmpty()) throw new ResourceNotFoundException("No application found");

        return convertToDTO(apps.get(0));
    }

    @Override
    public JobApplicationForCandidateDTO updateReferralStatus(String jobPrefix, String email, String referralStatus) {
        List<JobApplicationForCandidate> apps = applicationForCandidateRepository.findByJobPrefixAndEmail(jobPrefix, email);
        if (apps.isEmpty()) {
            throw new ResourceNotFoundException("No application found for the given job prefix and email.");
        }

        ReferralStatus newStatus;
        try {
            newStatus = ReferralStatus.valueOf(referralStatus.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Invalid referralStatus '" + referralStatus + "'. Allowed values: "
                            + Arrays.toString(ReferralStatus.values()));
        }

        JobApplicationForCandidate application = apps.get(0);
        application.setReferralStatus(newStatus);
        applicationForCandidateRepository.save(application);

        return convertToDTO(application);
    }

    @Override
    @Transactional
    public void shortlistCandidateWithoutAts(String jobPrefix, String email) {
        List<JobApplicationForCandidate> applications =
                applicationForCandidateRepository.findByJobPrefixAndEmail(jobPrefix, email);
        if (applications.isEmpty()) {
            throw new ResourceNotFoundException("No application found for the given job prefix and email.");
        }

        JobApplicationForCandidate application = applications.get(0);

        // Manual shortlist bypasses ATS but still respects the workflow: only an
        // APPLIED application may move to SHORTLISTED. Anything else (already
        // shortlisted, rejected, or further along) throws and is reported as failed.
        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.SHORTLISTED);

        application.setStatus(ApplicationStatus.SHORTLISTED);
        application.setShortlistStatus("Shortlisted");
        applicationForCandidateRepository.save(application);

        // Notify the candidate (email + WhatsApp), same as ATS shortlisting.
        // Best-effort: a notification failure must not fail an already-committed shortlist.
        try {
            Map<String, Object> emailParams = new HashMap<>();
            emailParams.put("recipientEmail", email);
            emailParams.put("fullName", application.getFirstName() + " " + application.getLastName());
            emailParams.put("mobileNumber", application.getMobileNumber());
            emailService.sendUniversalEmail(EmailType.SHORTLIST_NOTIFICATION, emailParams);
        } catch (Exception e) {
            logger.warn("Shortlist notification failed for {} (job {}): {}", email, jobPrefix, e.getMessage());
        }
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
	                         .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with id: " + id));
	    }
    
	    @Override
	    public void sendAcknowledgementMailAndUpdateStatus(String jobPrefix, String email, LocalDateTime examSlot) {
	        List<JobApplicationForCandidate> applications = applicationForCandidateRepository
	                .findByJobPrefixAndEmail(jobPrefix, email);
	        JobPost jobs = jobPostRepository.findByJobPrefix(jobPrefix)
	                .orElseThrow(() -> new ResourceNotFoundException("No job found for prefix: " + jobPrefix));

	        if (applications.isEmpty()) {
	            throw new ResourceNotFoundException("No application found for the given job prefix and email.");
	        }

	        // The slot is parsed and checked against the business clock at the endpoint,
	        // before any mail goes out; reaching here without one is a programming error.
	        if (examSlot == null) {
	            throw new IllegalArgumentException("Date & time is required.");
	        }

	        JobApplicationForCandidate application = applications.get(0);

	        // Validate workflow transition: SHORTLISTED → ACKNOWLEDGED
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.ACKNOWLEDGED);

	        try {
	            application.setStatus(ApplicationStatus.ACKNOWLEDGED);
	            application.setConfirmationStatus("Confirmation Sent");
	            application.setExamDate(examSlot.toLocalDate());
	            application.setExamTime(examSlot.toLocalTime());
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
	            // The template formats this for display; never hand it a pre-formatted or
	            // ISO string.
	            emailParams.put(EmailServiceImpl.EXAM_SCHEDULE_PARAM, examSlot);
	            emailParams.put("acknowledgeUrl", acknowledgeUrl);
	            emailParams.put("mobileNumber", application.getMobileNumber());

	            emailService.sendUniversalEmail(EmailType.ACKNOWLEDGEMENT, emailParams);

	            whatsAppService.sendWhatsAppMessage(
	                    application.getMobileNumber(),
	                    WhatsAppService.MessageType.EXAM_SCHEDULE,
	                    examSlot,
	                    examSlot.plusHours(1)
	                );

	        } catch (Exception e) {
	            throw new RuntimeException("Error during acknowledgement process: " + e.getMessage());
	        }
	    }


	    
	    @Override
	    public String acknowledgeCandidate(String jobPrefix, String email) {
	        List<JobApplicationForCandidate> applications =
	                applicationForCandidateRepository.findByJobPrefixAndEmail(jobPrefix, email);

	        if (applications.isEmpty()) {
	            throw new ResourceNotFoundException(
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
	        emailParams.put(EmailServiceImpl.EXAM_SCHEDULE_PARAM,
	                LocalDateTime.of(application.getExamDate(), application.getExamTime()));
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
	            throw new ResourceNotFoundException("No application found for the given job prefix and email.");
	        }

	        JobApplicationForCandidate application = applications.get(0);

	        // Validate workflow transition: ACKNOWLEDGED_BACK → RECONFIRMED
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.RECONFIRMED);

	        if (application.getExamDate() == null || application.getExamTime() == null) {
	            throw new ResourceNotFoundException("Exam date or time not found. Please send acknowledgement mail first.");
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
	        emailParams.put(EmailServiceImpl.EXAM_SCHEDULE_PARAM,
	                LocalDateTime.of(application.getExamDate(), application.getExamTime()));
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
	            throw new ResourceNotFoundException("No application found for the given job prefix and email.");
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
	            throw new ResourceNotFoundException("No job application found for the given job prefix and email");
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
	            throw new ResourceNotFoundException("No application found for the given job prefix and email.");
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
	            throw new ResourceNotFoundException("No application found for the given job prefix and email.");
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
	    @Transactional
	    public void sendExamLink(String jobPrefix, String email, LocalDateTime examSlot) {
	        List<JobApplicationForCandidate> applications = applicationForCandidateRepository
	                .findByJobPrefixAndEmail(jobPrefix, email);

	        if (applications.isEmpty()) {
	            throw new ResourceNotFoundException("No application found for the given job prefix and email.");
	        }

	        JobApplicationForCandidate application = applications.get(0);

	        // Source of truth: an exam must actually be assigned before we can send its
	        // link. Without this guard the candidate gets marked EXAM_SENT but has no
	        // assessment to take. Do NOT change status if none exists.
	        if (!assessmentRepository.existsByCandidateEmailAndJobPrefix(email, jobPrefix)) {
	            throw new IllegalStateException(
	                    "No assessment assigned for " + email + " on job " + jobPrefix
	                            + ". Assign an exam first (POST /api/assign) before sending the exam link.");
	        }

	        // Validate workflow transition: RECONFIRMED → EXAM_SENT
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.EXAM_SENT);

	        // No slot supplied means "start now" — in the business timezone, so a
	        // UTC-hosted server does not tell the candidate an exam started 5:30 ago.
	        LocalDateTime startTime = examSlot != null ? examSlot : businessSchedule.now();
	        LocalDateTime endTime = startTime.plusHours(1);

	        // Send the exam-schedule email FIRST. If it fails it throws, the transaction
	        // rolls back, and the candidate is NOT left marked EXAM_SENT.
	        emailService.sendExamLink(email, startTime, endTime, jobPrefix);

	        // Only after the email succeeds do we commit the status change.
	        application.setStatus(ApplicationStatus.EXAM_SENT);
	        application.setExamLinkStatus("Exam Link Sent");
	        applicationForCandidateRepository.save(application);

	        // WhatsApp is a best-effort secondary channel: its failure must not undo a
	        // successfully-sent exam link.
	        try {
	            whatsAppService.sendWhatsAppMessage(
	                    application.getMobileNumber(),
	                    WhatsAppService.MessageType.EXAM_SCHEDULE,
	                    startTime,
	                    endTime
	            );
	        } catch (Exception e) {
	            logger.warn("WhatsApp exam-schedule notification failed for {} (job {}): {}",
	                    email, jobPrefix, e.getMessage());
	        }
	    }

	    //method to schedule interview
	    public JobApplicationForCandidate scheduleInterview(String jobPrefix, String email) {

	        JobApplicationForCandidate application = applicationForCandidateRepository
	                .findByJobPost_JobPrefixAndUser_Email(jobPrefix, email)
	                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

	        // Validate workflow transition: EXAM_COMPLETED → INTERVIEW_SCHEDULED
	        StatusTransitionValidator.validate(application.getStatus(), ApplicationStatus.INTERVIEW_SCHEDULED);

	        application.setStatus(ApplicationStatus.INTERVIEW_SCHEDULED);
	        application.setInterview("Scheduled");

	        return applicationForCandidateRepository.save(application);
	    }


    
}