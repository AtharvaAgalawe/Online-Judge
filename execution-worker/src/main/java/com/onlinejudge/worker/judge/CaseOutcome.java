package com.onlinejudge.worker.judge;

import com.onlinejudge.common.enums.Verdict;

/**
 * Verdict of one executed test case (PRD §13 {@code submission_results} row).
 *
 * @param stdoutSnippet the user's own stdout, kept for sample tests only — hidden test
 *                      output is never stored or returned (PRD §7.4)
 * @param stderrSnippet the user's own stderr, kept for sample tests only and used solely
 *                      for the submission's error_message on a runtime error (PRD §17);
 *                      never persisted as a result row
 */
public record CaseOutcome(
        Long testCaseId,
        boolean sample,
        Verdict verdict,
        Integer timeUsedMs,
        Integer memoryUsedKb,
        String stdoutSnippet,
        String stderrSnippet) {
}
