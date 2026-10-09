package com.rightpath.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Which interview a phone's pairing token belongs to.
 *
 * <p>The phone has only the token from the QR code, and nothing on the server
 * connected that token to an interview — so a recording or photo arriving from
 * the phone could not be filed against anyone. The candidate's browser, signed
 * in and sitting a known interview, registers the token it just generated; from
 * then on the phone's uploads can be filed to that interview without the phone
 * ever holding a login.</p>
 *
 * <p>In the database rather than in memory so that it survives a restart and
 * is the same on every server instance.</p>
 */
@Entity
@Table(name = "interview_mobile_pairing", indexes = {
        @Index(name = "idx_mobile_pairing_interview", columnList = "interview_schedule_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewMobilePairing {

    @Id
    @Column(name = "token", length = 64)
    private String token;

    @Column(name = "interview_schedule_id", nullable = false)
    private Long interviewScheduleId;

    @Column(name = "candidate_email", nullable = false)
    private String candidateEmail;

    /** UTC. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Recording bytes accepted so far, so a token cannot be used to fill the bucket. */
    @Column(name = "bytes_received", nullable = false)
    @Builder.Default
    private long bytesReceived = 0L;
}
