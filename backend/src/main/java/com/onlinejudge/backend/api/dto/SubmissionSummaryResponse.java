package com.onlinejudge.backend.api.dto;

import java.time.Instant;

import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/**
 * History-list row (PRD §7.5): status, verdict, and timestamps. Source code is
 * deliberately not part of the list payload — it is available from the detail endpoint
 * to the owner (or an admin) only.
 */
public record SubmissionSummaryResponse(
        Long id,
        Long problemId,
        String problemSlug,
        String languageName,
        SubmissionStatus status,
        Verdict verdict,
        Integer timeUsedMs,
        Integer memoryUsedKb,
        Instant submittedAt) {

    public static SubmissionSummaryResponse from(Submission submission) {
        return new SubmissionSummaryResponse(
                submission.getId(),
                submission.getProblem().getId(),
                submission.getProblem().getSlug(),
                submission.getLanguage().getName(),
                submission.getStatus(),
                submission.getVerdict(),
                submission.getTimeUsedMs(),
                submission.getMemoryUsedKb(),
                submission.getSubmittedAt());
    }
}
