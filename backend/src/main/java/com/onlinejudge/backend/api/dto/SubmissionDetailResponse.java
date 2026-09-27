package com.onlinejudge.backend.api.dto;

import java.time.Instant;
import java.util.List;

import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/**
 * Full submission detail (PRD §7.4). Hidden test case content is never part of this
 * payload: the failed test case is identified only by its 1-based index and whether it
 * is a sample, and per-case results carry pass/fail verdicts without any test data.
 */
public record SubmissionDetailResponse(
        Long id,
        Long problemId,
        String problemSlug,
        String languageName,
        SubmissionStatus status,
        Verdict verdict,
        Integer timeUsedMs,
        Integer memoryUsedKb,
        String errorMessage,
        String sourceCode,
        Instant submittedAt,
        Instant judgedAt,
        FailedTestCase failedTestCase,
        List<CaseResult> results) {

    public record FailedTestCase(int index, boolean isSample) {
    }

    public record CaseResult(int index, boolean isSample, Verdict verdict, Integer timeUsedMs,
                             Integer memoryUsedKb) {
    }
}
