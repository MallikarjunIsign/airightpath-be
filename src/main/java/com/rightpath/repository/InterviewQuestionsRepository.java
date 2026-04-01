package com.rightpath.repository;

import com.rightpath.entity.UploadInterviewQuestions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewQuestionsRepository extends JpaRepository<UploadInterviewQuestions, Long> {

    // Get all files for a jobPrefix
    List<UploadInterviewQuestions> findByJobPrefix(String jobPrefix);

    // Get latest file (based on ID descending)
    List<UploadInterviewQuestions> findByJobPrefixOrderByIdDesc(String jobPrefix);
}