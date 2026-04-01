package com.rightpath.dto.voice;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeResponse {
    private boolean hasActiveSession;
    private Long scheduleId;
    private int currentQuestionIndex;
    private int warningCount;
    private List<ConversationEntryDTO> conversationHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationEntryDTO {
        private String role;
        private String content;
        private String timestamp;
    }
}
