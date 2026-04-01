package com.rightpath.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewStatsDTO {
    private long totalInterviews;
    private double passRate;
    private double avgScore;
    private double avgDurationMinutes;
}
