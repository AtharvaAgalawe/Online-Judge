package com.onlinejudge.common.exception;

/**
 * Thrown when a submission status transition is attempted that the lifecycle graph
 * (PRD §17) does not permit. Indicates a bug or an unexpected concurrent writer.
 */
public class IllegalStateTransitionException extends RuntimeException {

    private final Long submissionId;
    private final String current;
    private final String target;

    public IllegalStateTransitionException(Long submissionId, Enum<?> current, Enum<?> target) {
        super("Illegal submission status transition for submission %s: %s -> %s"
                .formatted(submissionId == null ? "(unsaved)" : submissionId, current, target));
        this.submissionId = submissionId;
        this.current = current.name();
        this.target = target.name();
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public String getCurrent() {
        return current;
    }

    public String getTarget() {
        return target;
    }
}
