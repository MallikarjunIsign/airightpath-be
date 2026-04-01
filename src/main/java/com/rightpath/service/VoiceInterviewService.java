package com.rightpath.service;

import com.rightpath.dto.voice.ResumeResponse;
import com.rightpath.dto.voice.VoiceAnswerRequest;
import com.rightpath.dto.voice.VoiceEvaluationResult;
import com.rightpath.dto.voice.VoiceSessionStatus;
import com.rightpath.dto.voice.VoiceStartResponse;

public interface VoiceInterviewService {

    VoiceStartResponse startVoiceInterview(String jobPrefix, String email);

    void processVoiceAnswer(Long scheduleId, VoiceAnswerRequest request);

    void endVoiceInterview(Long scheduleId);

    boolean handleWarning(Long scheduleId);

    VoiceSessionStatus getSessionStatus(Long scheduleId);

    VoiceEvaluationResult getEvaluation(Long scheduleId);

    ResumeResponse resumeInterview(Long scheduleId);
}
