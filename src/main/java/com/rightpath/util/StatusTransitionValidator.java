package com.rightpath.util;

import java.util.Map;
import java.util.Set;

import com.rightpath.enums.ApplicationStatus;

/**
 * Validates status transitions for job applications.
 * Enforces strict linear progression through the recruitment pipeline.
 */
public class StatusTransitionValidator {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.ofEntries(
        Map.entry(ApplicationStatus.APPLIED,               Set.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED)),
        Map.entry(ApplicationStatus.SHORTLISTED,           Set.of(ApplicationStatus.ACKNOWLEDGED, ApplicationStatus.REJECTED)),
        Map.entry(ApplicationStatus.ACKNOWLEDGED,          Set.of(ApplicationStatus.ACKNOWLEDGED_BACK)),
        Map.entry(ApplicationStatus.ACKNOWLEDGED_BACK,     Set.of(ApplicationStatus.RECONFIRMED, ApplicationStatus.REJECTED)),
        Map.entry(ApplicationStatus.RECONFIRMED,           Set.of(ApplicationStatus.EXAM_SENT, ApplicationStatus.REJECTED)),
        Map.entry(ApplicationStatus.EXAM_SENT,             Set.of(ApplicationStatus.EXAM_COMPLETED)),
        Map.entry(ApplicationStatus.EXAM_COMPLETED,        Set.of(ApplicationStatus.INTERVIEW_SCHEDULED, ApplicationStatus.REJECTED)),
        Map.entry(ApplicationStatus.INTERVIEW_SCHEDULED,   Set.of(ApplicationStatus.INTERVIEW_COMPLETED)),
        Map.entry(ApplicationStatus.INTERVIEW_COMPLETED,   Set.of(ApplicationStatus.SELECTED, ApplicationStatus.REJECTED))
    );

    /**
     * Validates whether a status transition is allowed.
     *
     * @param currentStatus the current status of the application
     * @param targetStatus  the desired next status
     * @throws IllegalStateException if the transition is not allowed
     */
    public static void validate(ApplicationStatus currentStatus, ApplicationStatus targetStatus) {
        if (currentStatus == null) {
            throw new IllegalStateException("Current status is missing. Cannot transition to " + targetStatus + ".");
        }

        if (currentStatus == ApplicationStatus.REJECTED) {
            throw new IllegalStateException("Cannot transition from REJECTED. The application has been closed.");
        }

        if (currentStatus == ApplicationStatus.SELECTED) {
            throw new IllegalStateException("Cannot transition from SELECTED. The application is already finalized.");
        }

        Set<ApplicationStatus> allowed = ALLOWED_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new IllegalStateException(
                "Cannot transition from " + currentStatus + " to " + targetStatus + ". " +
                "Allowed transitions from " + currentStatus + ": " +
                (allowed != null ? allowed : "none") + "."
            );
        }
    }
}
