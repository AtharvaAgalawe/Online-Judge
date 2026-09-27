package com.onlinejudge.worker.judge;

import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;
import com.onlinejudge.worker.execution.RunOutcome;

/**
 * Maps one sandbox run to a verdict (PRD §17): the watchdog's timeout wins over
 * everything (the process was killed, so its exit code is meaningless); exit 137 without
 * a timeout is the cgroup memory kill; any other non-zero exit is a runtime error;
 * truncated output is a presentation failure; otherwise exact comparison decides.
 */
public final class VerdictClassifier {

    private static final int SIGKILL_EXIT_CODE = 137;

    private VerdictClassifier() {
    }

    public static Verdict classify(RunOutcome outcome, String expectedOutput) {
        if (outcome.timedOut()) {
            return Verdict.TIME_LIMIT_EXCEEDED;
        }
        if (outcome.exitCode() == SIGKILL_EXIT_CODE) {
            return Verdict.MEMORY_LIMIT_EXCEEDED;
        }
        if (outcome.exitCode() != 0) {
            return Verdict.RUNTIME_ERROR;
        }
        if (outcome.stdoutTruncated()) {
            return Verdict.WRONG_ANSWER;
        }
        return OutputComparator.matches(outcome.stdout(), expectedOutput)
                ? Verdict.ACCEPTED
                : Verdict.WRONG_ANSWER;
    }

    /** Terminal pipeline status for a final verdict (PRD §17 lifecycle). */
    public static SubmissionStatus terminalStatus(Verdict verdict) {
        return switch (verdict) {
            case ACCEPTED, WRONG_ANSWER -> SubmissionStatus.COMPLETED;
            case TIME_LIMIT_EXCEEDED -> SubmissionStatus.TIME_LIMIT_EXCEEDED;
            case MEMORY_LIMIT_EXCEEDED -> SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
            case RUNTIME_ERROR -> SubmissionStatus.RUNTIME_ERROR;
            case COMPILATION_ERROR -> SubmissionStatus.COMPILATION_ERROR;
            case SYSTEM_ERROR -> SubmissionStatus.SYSTEM_ERROR;
        };
    }
}
