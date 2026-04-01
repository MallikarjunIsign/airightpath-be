package com.rightpath.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rightpath.entity.JobPrompt;
import com.rightpath.enums.PromptStage;
import com.rightpath.enums.PromptType;

public interface JobPromptRepository extends JpaRepository<JobPrompt, Long> {

	Optional<JobPrompt> findByPromptType(String promptType);

	Optional<JobPrompt> findByJobPrefixAndPromptType(String jobPrefix, PromptType type);

	Optional<JobPrompt> findByJobPrefix(String jobPrefix);

	List<JobPrompt> findAllByJobPrefix(String jobPrefix);

	Optional<JobPrompt> findByJobPrefixAndPromptTypeAndPromptStage(String jobPrefix, PromptType promptType,
			PromptStage promptStage);

}
