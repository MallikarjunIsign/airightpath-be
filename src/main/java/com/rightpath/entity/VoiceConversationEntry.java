package com.rightpath.entity;

import java.time.LocalDateTime;

import com.rightpath.enums.ConversationRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VoiceConversationEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_schedule_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CandidateInterviewSchedule interviewSchedule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationRole role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private Integer wordCount;
    private Double wordsPerMinute;
    private Integer fillerWordCount;
    private Double confidenceScore;
    private Double speechDurationSeconds;

    @Column(columnDefinition = "TEXT")
    private String codeContent;

    @Column(length = 20)
    private String codeLanguage;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

}
