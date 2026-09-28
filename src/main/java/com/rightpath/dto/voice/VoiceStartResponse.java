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

    /**
     * True when this call picked up an interview already under way rather than
     * opening a new one.
     *
     * <p>The server has always resumed correctly — it keeps the transcript and
     * the question count and hands back the question that was outstanding. The
     * browser had no way to tell the two apart, so after a refresh it rebuilt
     * its screen as though the interview had just begun: empty transcript,
     * counter back at one. The candidate saw an interview that had apparently
     * lost everything they had said, and the screen told them it could not be
     * resumed.</p>
     */
    private boolean resumed;

    /** Turns taken so far, so a resumed screen shows the real position. */
    private int questionsAsked;
}
