package com.onlinejudge.common.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.exception.IllegalStateTransitionException;

/**
 * Verifies the submission lifecycle graph from PRD §17 — the single most
 * correctness-critical invariant in the system (monotonic status progression).
 */
class SubmissionLifecycleTest {

    @Test
    void happyPathReachesCompleted() {
        Submission submission = new Submission(null, null, null, "code", null);
        submission.transitionTo(SubmissionStatus.QUEUED);
        submission.transitionTo(SubmissionStatus.PICKED_UP);
        submission.transitionTo(SubmissionStatus.COMPILING);
        submission.transitionTo(SubmissionStatus.RUNNING);
        submission.transitionTo(SubmissionStatus.EVALUATING);
        submission.transitionTo(SubmissionStatus.COMPLETED);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.COMPLETED);
        assertThat(SubmissionStatus.COMPLETED.isTerminal()).isTrue();
    }

    @Test
    void interpretedLanguagesMaySkipCompiling() {
        Submission submission = new Submission(null, null, null, "code", null);
        submission.transitionTo(SubmissionStatus.QUEUED);
        submission.transitionTo(SubmissionStatus.PICKED_UP);
        submission.transitionTo(SubmissionStatus.RUNNING);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.RUNNING);
    }

    @Test
    void compileErrorIsTerminalFromCompiling() {
        Submission submission = new Submission(null, null, null, "code", null);
        submission.transitionTo(SubmissionStatus.QUEUED);
        submission.transitionTo(SubmissionStatus.PICKED_UP);
        submission.transitionTo(SubmissionStatus.COMPILING);
        submission.transitionTo(SubmissionStatus.COMPILATION_ERROR);

        assertThat(SubmissionStatus.COMPILATION_ERROR.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SubmissionStatus.class, names = {"TIME_LIMIT_EXCEEDED", "MEMORY_LIMIT_EXCEEDED",
            "RUNTIME_ERROR", "SYSTEM_ERROR"})
    void runningMayTransitionToEveryTerminalFailure(SubmissionStatus terminal) {
        Submission submission = submissionInStatus(SubmissionStatus.RUNNING);
        submission.transitionTo(terminal);

        assertThat(terminal.isTerminal()).isTrue();
    }

    @Test
    void statusNeverRegressesOutsideReconciliation() {
        Submission submission = submissionInStatus(SubmissionStatus.COMPILING);

        assertThatThrownBy(() -> submission.transitionTo(SubmissionStatus.SUBMITTED))
                .isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> submission.transitionTo(SubmissionStatus.QUEUED))
                .isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> submission.transitionTo(SubmissionStatus.PICKED_UP))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void terminalStatusHasNoOutgoingTransitions() {
        Submission submission = submissionInStatus(SubmissionStatus.COMPLETED);

        assertThatThrownBy(() -> submission.transitionTo(SubmissionStatus.RUNNING))
                .isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> submission.transitionTo(SubmissionStatus.SYSTEM_ERROR))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void selfTransitionIsPermittedForRedeliveredJobs() {
        Submission submission = submissionInStatus(SubmissionStatus.RUNNING);
        submission.transitionTo(SubmissionStatus.RUNNING);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.RUNNING);
    }

    @Test
    void verdictOnlySettableOnceAtTerminalTransition() {
        Submission submission = submissionInStatus(SubmissionStatus.EVALUATING);
        submission.applyTerminalOutcome(SubmissionStatus.COMPLETED, Verdict.ACCEPTED,
                120, 65_000, null, null);

        assertThatThrownBy(() -> submission.applyTerminalOutcome(SubmissionStatus.COMPLETED,
                Verdict.WRONG_ANSWER, 999, 999, null, null))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void nonTerminalStatusCannotCarryTerminalOutcome() {
        Submission submission = submissionInStatus(SubmissionStatus.RUNNING);

        assertThatThrownBy(() -> submission.applyTerminalOutcome(SubmissionStatus.EVALUATING,
                Verdict.ACCEPTED, 1, 1, null, null))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    private Submission submissionInStatus(SubmissionStatus status) {
        Submission submission = new Submission(null, null, null, "code", null);
        SubmissionStatus[] path = {SubmissionStatus.QUEUED, SubmissionStatus.PICKED_UP,
                SubmissionStatus.COMPILING, SubmissionStatus.RUNNING, SubmissionStatus.EVALUATING,
                SubmissionStatus.COMPLETED};
        for (SubmissionStatus step : path) {
            if (submission.getStatus().canTransitionTo(step)) {
                submission.transitionTo(step);
            }
            if (submission.getStatus() == status) {
                return submission;
            }
        }
        if (submission.getStatus() != status) {
            throw new IllegalStateException("Test fixture could not reach status " + status);
        }
        return submission;
    }
}
