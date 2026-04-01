package com.rightpath.repository;

import com.rightpath.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {
    Optional<InterviewQuestion> findByInterviewScheduleIdAndUniqueId(Long scheduleId, String uniqueId);
}