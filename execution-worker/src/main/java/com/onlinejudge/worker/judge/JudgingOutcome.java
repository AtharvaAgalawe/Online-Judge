package com.onlinejudge.worker.judge;

import java.util.List;

import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/**
 * Everything the final transactional write needs (PRD §13): the terminal status and
 * verdict, aggregated resource usage (the maximum across executed test cases, PRD §20),
 * the first failing test case, and one row per executed test case.
 */
public record JudgingOutcome(
        long submissionId,
        SubmissionStatus terminalStatus,
        Verdict verdict,
        Integer timeUsedMs,
        Integer memoryUsedKb,
        Long failedTestCaseId,
        String errorMessage,
        List<CaseOutcome> results) {

    public JudgingOutcome {
        results = List.copyOf(results);
    }
}
