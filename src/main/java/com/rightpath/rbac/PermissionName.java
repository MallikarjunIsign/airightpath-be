package com.rightpath.rbac;

/**
 * Canonical permissions for method-level authorization.
 *
 * Keep names stable once used in @PreAuthorize.
 */
public enum PermissionName {
    // User / profile
    USER_READ,
    USER_UPDATE,
    USER_LIST,
    USER_ACTIVATE,
    USER_DEACTIVATE,

    // Resume
    RESUME_UPLOAD,
    RESUME_UPDATE,
    RESUME_VIEW,
    RESUME_VIEW_ALL,

    // ATS
    ATS_UPLOAD_SINGLE,
    ATS_UPLOAD_MULTI,

    // Assessments
    ASSESSMENT_UPLOAD,
    ASSESSMENT_ASSIGN,
    ASSESSMENT_SUBMIT,
    ASSESSMENT_RESULT_SUBMIT,

    // Questions
    QUESTION_GENERATE,
    CODING_QUESTION_GENERATE,

    // Job posts & applications
    JOB_POST_CREATE,
    JOB_POST_READ,
    JOB_APPLY,
    JOB_APPLICATION_READ_ALL,

    // Interview
    INTERVIEW_ASSIGN,
    INTERVIEW_START,
    INTERVIEW_ANSWER,

    // Compiler
    COMPILER_RUN,
    COMPILER_RESULTS_READ,
    
    ATS_READ,
    ASSESSMENT_READ,
    JOB_WRITE,
    INTERVIEW_WRITE,
    QUESTION_WRITE
}
