

package com.rightpath.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rightpath.entity.Question;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    
}
