package com.rightpath.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Table(name = "interview_question",
       uniqueConstraints = {@UniqueConstraint(columnNames = {"interview_schedule_id", "unique_id"})})
@Data
public class InterviewQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_schedule_id", nullable = false)
    private Long interviewScheduleId;

    @Column(name = "unique_id", nullable = false)
    private String uniqueId;

    @Column(name = "level")
    private String level;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "count", nullable = false)
    private int count;
}