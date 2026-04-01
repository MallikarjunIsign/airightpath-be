package com.rightpath.dto.voice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceSessionStatus {
    private Long scheduleId;
    private String status;
    private int totalQuestionsAsked;
    private int warningCount;
    private LocalDateTime startedAt;
    private String interviewerName;
}
