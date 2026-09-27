package com.onlinejudge.worker.judge;

import java.util.List;

/**
 * Immutable snapshot of everything needed to judge a submission, loaded in one short
 * read-only transaction before any container work starts — judging must never hold a
 * database transaction open while containers run.
 *
 * @param timeLimitMs   already multiplied by the language's time-limit multiplier (PRD §26)
 */
public record JudgingPlan(
        long submissionId,
        String sourceCode,
        String dockerImage,
        String sourceFilename,
        String compileCmd,
        String runCmd,
        int timeLimitMs,
        int memoryLimitKb,
        List<PlannedTestCase> testCases) {

    public JudgingPlan {
        testCases = List.copyOf(testCases);
    }

    /** One test case as the judge will execute it. */
    public record PlannedTestCase(Long id, String input, String expectedOutput, boolean sample, int order) {
    }
}
