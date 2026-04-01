package com.rightpath.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rightpath.entity.EvaluationCategory;

public interface EvaluationCategoryRepository extends JpaRepository<EvaluationCategory, Long> {

    List<EvaluationCategory> findAllByJobPrefix(String jobPrefix);

    void deleteAllByJobPrefix(String jobPrefix);
}
