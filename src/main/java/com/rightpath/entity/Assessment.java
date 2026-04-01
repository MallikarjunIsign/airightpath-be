package com.rightpath.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.rightpath.enums.AssessmentType;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Assessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AssessmentType assessmentType;
    @Column(name = "uploaded_by")
    private String uploadedBy;
    @Column(name = "candidate_email")
    private String candidateEmail;

    @Column(columnDefinition = "TEXT")
    private String questionPaper;

    @Nullable
    @Lob
    private byte[] answerKey;

    private boolean examAttended = false;
    private boolean expired = false;
    private boolean adminAcceptance = false;
    private String adminComments;

    private LocalDateTime assignedAt;
    private LocalDateTime startTime;
    private LocalDateTime deadline;

    @OneToOne(mappedBy = "assessment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private Result result;

    @Column(name = "job_prefix")
    private String jobPrefix;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_email", referencedColumnName = "email", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Users candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_prefix", referencedColumnName = "job_prefix", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private JobPost jobPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", referencedColumnName = "email", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Users uploader;

    @Column(length = 100)
    private String containerName;

    @Column(length = 100, unique = true)
    private String fileName;

}
