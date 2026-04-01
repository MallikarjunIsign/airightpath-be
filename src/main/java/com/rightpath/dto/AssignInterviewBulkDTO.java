package com.rightpath.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssignInterviewBulkDTO {
    private List<String> emails;
    private String jobPrefix;
    private LocalDateTime assignedAt;
    private LocalDateTime deadlineTime;
    private boolean sendEmail = true;
}
