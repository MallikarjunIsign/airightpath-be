package com.rightpath.dto.voice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceStartResponse {
    private Long scheduleId;
    private String firstQuestion;
    private String interviewerName;
    private String firstQuestionAudio; // base64 encoded mp3
}
