package com.rightpath.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.rightpath.dto.JobListingResponse;
import com.rightpath.dto.JobPostDTO;
import com.rightpath.dto.JobPostSearchRequest;
import com.rightpath.dto.JobStatusCountsDTO;
import com.rightpath.entity.JobApplicationForCandidate;
import com.rightpath.entity.JobPost;
import com.rightpath.entity.Users;
import com.rightpath.enums.JobStatusFilter;
import com.rightpath.repository.JobApplicationForCandidateRepository;
import com.rightpath.repository.JobPostRepository;
import com.rightpath.repository.JobPostSpecifications;
import com.rightpath.repository.JobPostRepository.JobTypeCount;
import com.rightpath.repository.UsersRepository;
import com.rightpath.service.JobPostService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JobPostServiceImpl implements JobPostService {

	@Autowired
	private JobPostRepository repository;
	@Autowired
	private UsersRepository usersRepository;
	@Autowired
	private JobApplicationForCandidateRepository jobApplicationRepository;

	/**
	 * Timezone the business operates in. Deadlines are plain dates, so "is this
	 * job still active?" must be answered against the date it is <em>here</em> —
	 * not in the caller's zone and not in the database session's zone, either of
	 * which could expire a posting hours early or late.
	 */
	@Value("${app.business-timezone:Asia/Kolkata}")
	private String businessTimezone;

	/** Default page size when the client does not ask for one. */
	private static final int DEFAULT_PAGE_SIZE = 20;

	/** Hard ceiling on page size, to keep a single request bounded. */
	private static final int MAX_PAGE_SIZE = 100;

	/** Applied when the client does not ask for a specific ordering. */
	private static final String DEFAULT_SORT = "applicationDeadline,asc";

	/**
	 * Sortable fields, keyed by their lower-cased request spelling. Restricting the
	 * set keeps an unknown/unsortable property from reaching the query, and keeps
	 * the supported orderings visible in one place.
	 */
	private static final Map<String, String> SORTABLE_FIELDS = Map.of(
			"applicationdeadline", JobPostSpecifications.FIELD_APPLICATION_DEADLINE,
			"createdat", "createdAt",
			"jobtitle", "jobTitle",
			"companyname", "companyName",
			"location", "location",
			"jobtype", JobPostSpecifications.FIELD_JOB_TYPE,
			"id", "id");

	/**
	 * Final tiebreaker on every ordering. Without it, rows sharing a deadline (or a
	 * creation date) could be returned in a different order per page request, so
	 * paging would silently duplicate and skip rows.
	 */
	private static final Sort.Order ID_TIEBREAKER = Sort.Order.desc("id");

	public JobPost createJobPost(JobPostDTO dto) {
		Long lastId = repository.findTopByOrderByIdDesc().map(JobPost::getId).orElse(0L);

		String prefix = dto.getJobPrefix() != null ? dto.getJobPrefix().toUpperCase() : "JOB";
		String jobCode = String.format("%s-%03d", prefix, lastId + 1);

		JobPost jobPost = JobPost.builder().jobPrefix(jobCode).jobTitle(dto.getJobTitle())
				.companyName(dto.getCompanyName()).location(dto.getLocation()).jobDescription(dto.getJobDescription())
				.keySkills(dto.getKeySkills()).experience(dto.getExperience()).education(dto.getEducation())
				.salaryRange(dto.getSalaryRange()).jobType(dto.getJobType()).industry(dto.getIndustry())
				.department(dto.getDepartment()).role(dto.getRole()).numberOfOpenings(dto.getNumberOfOpenings())
				.contactEmail(dto.getContactEmail()).applicationDeadline(dto.getApplicationDeadline())
				.createdAt(LocalDate.now()).build();

		return repository.save(jobPost);
	}

	// Newest first: id is IDENTITY-generated, so higher id = more recently created.
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "id");

	@Override
	public List<JobPostDTO> getAllJobPosts() {
		return repository.findAll(NEWEST_FIRST).stream().map(this::convertToDTO).collect(Collectors.toList());
	}

	@Override
	public JobListingResponse searchJobPosts(JobPostSearchRequest request) {
		JobStatusFilter status = JobStatusFilter.fromParam(request.getStatus());
		Pageable pageable = toPageable(request);

		// Search and job type also bound the counts; status deliberately does not,
		// since the dropdown shows how many rows each bucket *would* return.
		Specification<JobPost> filters = JobPostSpecifications.combine(
				JobPostSpecifications.matchesSearch(request.getSearch()),
				JobPostSpecifications.matchesJobType(request.getJobType()));
		Specification<JobPost> statusFilter = JobPostSpecifications.hasStatus(status, businessToday());

		Page<JobPostDTO> page = repository
				.findAll(JobPostSpecifications.combine(filters, statusFilter), pageable)
				.map(this::convertToDTO);

		log.debug("Job listing: status={} search={} jobType={} -> {} of {} matches, page {}/{}",
				status, request.getSearch(), request.getJobType(), page.getNumberOfElements(),
				page.getTotalElements(), page.getNumber(), page.getTotalPages());

		return new JobListingResponse(page, countBuckets(filters));
	}

	@Override
	public JobStatusCountsDTO getStatusCounts(String search, String jobType) {
		return countBuckets(JobPostSpecifications.combine(
				JobPostSpecifications.matchesSearch(search),
				JobPostSpecifications.matchesJobType(jobType)));
	}

	@Override
	public List<String> getDistinctJobTypes() {
		// Group the stored spellings by the key the jobType filter compares on, so
		// each dropdown entry maps to exactly one result set. Within a group the
		// most-used spelling wins (ties broken alphabetically) to give a stable,
		// human-looking label.
		Map<String, JobTypeCount> canonicalByKey = new LinkedHashMap<>();
		for (JobTypeCount candidate : repository.findJobTypeCounts()) {
			String key = JobPostSpecifications.normalizeJobType(candidate.getJobType());
			if (key == null) {
				continue;
			}
			canonicalByKey.merge(key, candidate, JobPostServiceImpl::preferredSpelling);
		}

		List<String> jobTypes = canonicalByKey.values().stream()
				.map(jobType -> jobType.getJobType().trim())
				.sorted(Comparator.comparing(label -> label.toLowerCase()))
				.collect(Collectors.toList());
		log.debug("Resolved {} distinct job types from {} stored spellings", jobTypes.size(), canonicalByKey.size());
		return jobTypes;
	}

	/** Picks the spelling to display for a job type: most used, then alphabetical. */
	private static JobTypeCount preferredSpelling(JobTypeCount current, JobTypeCount candidate) {
		if (candidate.getOccurrences() != current.getOccurrences()) {
			return candidate.getOccurrences() > current.getOccurrences() ? candidate : current;
		}
		return candidate.getJobType().compareToIgnoreCase(current.getJobType()) < 0 ? candidate : current;
	}

	/**
	 * Counts the active bucket and the unfiltered total with the same predicates
	 * the listing uses, then derives expired — one fewer query than counting both
	 * buckets, and it guarantees {@code all == active + expired}.
	 */
	private JobStatusCountsDTO countBuckets(Specification<JobPost> filters) {
		long all = repository.count(filters);
		long active = repository.count(JobPostSpecifications.combine(filters,
				JobPostSpecifications.hasStatus(JobStatusFilter.ACTIVE, businessToday())));
		return new JobStatusCountsDTO(all, active, all - active);
	}

	/** Today's date in the configured business timezone. */
	private LocalDate businessToday() {
		return LocalDate.now(ZoneId.of(businessTimezone));
	}

	/** Applies paging defaults and limits, and resolves the requested ordering. */
	private static Pageable toPageable(JobPostSearchRequest request) {
		int page = request.getPage() != null ? request.getPage() : 0;
		int requestedSize = request.getSize() != null ? request.getSize() : DEFAULT_PAGE_SIZE;
		if (page < 0 || requestedSize < 1) {
			throw new IllegalArgumentException("page must be >= 0 and size must be >= 1");
		}
		int size = Math.min(requestedSize, MAX_PAGE_SIZE);
		if (size != requestedSize) {
			log.debug("Capping requested page size {} to {}", requestedSize, MAX_PAGE_SIZE);
		}
		return PageRequest.of(page, size, parseSort(request.getSort()));
	}

	/**
	 * Parses Spring's {@code field,direction} sort syntax against
	 * {@link #SORTABLE_FIELDS}, tolerating an omitted direction (defaults to
	 * ascending) and multiple pairs ({@code createdAt,desc,jobTitle,asc}). An
	 * {@code id} tiebreaker is always appended.
	 *
	 * @param rawSort the {@code sort} parameter; {@code null}/blank uses the default
	 * @return the resolved sort, never unordered
	 * @throws IllegalArgumentException if a field is not sortable, or a direction
	 *                                  appears before any field
	 */
	static Sort parseSort(String rawSort) {
		String sortExpression = (rawSort == null || rawSort.isBlank()) ? DEFAULT_SORT : rawSort;

		List<Sort.Order> orders = new ArrayList<>();
		for (String token : sortExpression.split(",")) {
			String value = token.trim();
			if (value.isEmpty()) {
				continue;
			}
			if (value.equalsIgnoreCase("asc") || value.equalsIgnoreCase("desc")) {
				if (orders.isEmpty()) {
					throw new IllegalArgumentException(
							"Invalid sort '" + rawSort + "': direction '" + value + "' has no field before it");
				}
				int last = orders.size() - 1;
				orders.set(last, orders.get(last).with(Sort.Direction.fromString(value)));
				continue;
			}
			String property = SORTABLE_FIELDS.get(value.toLowerCase());
			if (property == null) {
				throw new IllegalArgumentException("Cannot sort by '" + value + "'. Sortable fields: "
						+ SORTABLE_FIELDS.values().stream().sorted().collect(Collectors.joining(", ")));
			}
			orders.add(Sort.Order.asc(property));
		}
		if (orders.isEmpty()) {
			throw new IllegalArgumentException("Invalid sort '" + rawSort + "': no field given");
		}

		boolean alreadyDeterministic = orders.stream().anyMatch(order -> "id".equals(order.getProperty()));
		if (!alreadyDeterministic) {
			orders.add(ID_TIEBREAKER);
		}
		return Sort.by(orders);
	}

	@Override
	public JobPostDTO convertToDTO(JobPost post) {
		return JobPostDTO.builder().jobPrefix(post.getJobPrefix()).jobTitle(post.getJobTitle())
				.companyName(post.getCompanyName()).location(post.getLocation())
				.jobDescription(post.getJobDescription()).keySkills(post.getKeySkills())
				.experience(post.getExperience()).education(post.getEducation()).salaryRange(post.getSalaryRange())
				.jobType(post.getJobType()).industry(post.getIndustry()).department(post.getDepartment())
				.role(post.getRole()).numberOfOpenings(post.getNumberOfOpenings()).contactEmail(post.getContactEmail())
				.applicationDeadline(post.getApplicationDeadline()).build();
	}

	public String applyToJob(Long jobId, String userEmail) {
		JobPost jobPost = repository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));

		Users user = usersRepository.findById(userEmail).orElseThrow(() -> new RuntimeException("User not found"));

		// Check if user already applied via existing job applications
		boolean alreadyApplied = jobApplicationRepository
				.findByJobPost_JobPrefixAndUser_Email(jobPost.getJobPrefix(), userEmail)
				.isPresent();
		if (alreadyApplied) {
			return "User already applied for this job.";
		}

		JobApplicationForCandidate application = JobApplicationForCandidate.builder()
				.user(user)
				.jobPost(jobPost)
				.firstName(user.getFirstName())
				.lastName(user.getLastName())
				.build();
		jobApplicationRepository.save(application);
		return "Application submitted successfully.";
	}

	@Override
	public int getApplicationCount(Long jobId) {
		JobPost jobPost = repository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found"));
		return jobApplicationRepository.findByJobPost(jobPost).size();
	}

	@Override
	public JobPostDTO findByJobPrefix(String jobPrefix) {
		// TODO Auto-generated method stub
		return null;
	}

}
