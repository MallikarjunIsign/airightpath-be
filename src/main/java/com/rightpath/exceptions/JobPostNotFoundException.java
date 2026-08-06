package com.rightpath.exceptions;

/**
 * Thrown when a job post lookup by id finds nothing.
 *
 * <p>Distinct from {@link ResourceNotFoundException} so the response carries the
 * {@code JOB_NOT_FOUND} code the job screens switch on, rather than the generic
 * {@code RESOURCE_NOT_FOUND}. Handled as HTTP 404.
 */
public class JobPostNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JobPostNotFoundException(Long id) {
        super("No job post found with id " + id + ".");
    }
}
