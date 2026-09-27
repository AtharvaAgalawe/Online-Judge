package com.onlinejudge.worker.judge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;
import com.onlinejudge.worker.execution.RunOutcome;

class VerdictClassifierTest {

    @Test
    void watchdogTimeoutWinsOverExitCode() {
        RunOutcome outcome = outcome(137, true, false, "", "", 5_000);

        assertThat(VerdictClassifier.classify(outcome, "5")).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void sigkillWithoutTimeoutIsMemoryLimitExceeded() {
        RunOutcome outcome = outcome(137, false, false, "", "", 200);

        assertThat(VerdictClassifier.classify(outcome, "5")).isEqualTo(Verdict.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    void nonZeroExitIsRuntimeError() {
        RunOutcome outcome = outcome(1, false, false, "", "Traceback", 200);

        assertThat(VerdictClassifier.classify(outcome, "5")).isEqualTo(Verdict.RUNTIME_ERROR);
    }

    @Test
    void truncatedOutputIsAPresentationFailure() {
        RunOutcome outcome = outcome(0, false, true, "5", "", 200);

        assertThat(VerdictClassifier.classify(outcome, "5")).isEqualTo(Verdict.WRONG_ANSWER);
    }

    @Test
    void matchingOutputIsAccepted() {
        RunOutcome outcome = outcome(0, false, false, "5 \n", "", 200);

        assertThat(VerdictClassifier.classify(outcome, "5\n")).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void differentOutputIsWrongAnswer() {
        RunOutcome outcome = outcome(0, false, false, "6\n", "", 200);

        assertThat(VerdictClassifier.classify(outcome, "5\n")).isEqualTo(Verdict.WRONG_ANSWER);
    }

    @Test
    void verdictsMapToTerminalStatuses() {
        assertThat(VerdictClassifier.terminalStatus(Verdict.ACCEPTED)).isEqualTo(SubmissionStatus.COMPLETED);
        assertThat(VerdictClassifier.terminalStatus(Verdict.WRONG_ANSWER)).isEqualTo(SubmissionStatus.COMPLETED);
        assertThat(VerdictClassifier.terminalStatus(Verdict.TIME_LIMIT_EXCEEDED))
                .isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
        assertThat(VerdictClassifier.terminalStatus(Verdict.MEMORY_LIMIT_EXCEEDED))
                .isEqualTo(SubmissionStatus.MEMORY_LIMIT_EXCEEDED);
        assertThat(VerdictClassifier.terminalStatus(Verdict.RUNTIME_ERROR))
                .isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        assertThat(VerdictClassifier.terminalStatus(Verdict.COMPILATION_ERROR))
                .isEqualTo(SubmissionStatus.COMPILATION_ERROR);
        assertThat(VerdictClassifier.terminalStatus(Verdict.SYSTEM_ERROR))
                .isEqualTo(SubmissionStatus.SYSTEM_ERROR);
    }

    private static RunOutcome outcome(int exitCode, boolean timedOut, boolean truncated, String stdout,
                                      String stderr, long durationMillis) {
        return new RunOutcome(exitCode, timedOut, truncated, stdout, stderr, durationMillis, 0);
    }
}
