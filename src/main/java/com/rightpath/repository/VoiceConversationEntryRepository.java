package com.rightpath.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rightpath.entity.VoiceConversationEntry;

public interface VoiceConversationEntryRepository extends JpaRepository<VoiceConversationEntry, Long> {

    List<VoiceConversationEntry> findByInterviewScheduleIdOrderByTimestampAsc(Long interviewScheduleId);
}
