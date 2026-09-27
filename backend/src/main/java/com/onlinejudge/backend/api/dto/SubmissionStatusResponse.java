package com.onlinejudge.backend.api.dto;

import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/**
 * Lightweight polling response (PRD §15). {@code verdict} is null until the submission
 * reaches a terminal state — it is never a placeholder string.
 */
public record SubmissionStatusResponse(SubmissionStatus status, Verdict verdict) {
}
