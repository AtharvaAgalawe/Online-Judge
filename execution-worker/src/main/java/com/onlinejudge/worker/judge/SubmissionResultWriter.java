package com.onlinejudge.worker.judge;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.SubmissionResult;
import com.onlinejudge.common.entity.TestCase;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;
import com.onlinejudge.worker.repository.SubmissionRepository;
import com.onlinejudge.worker.repository.SubmissionResultRepository;
import com.onlinejudge.worker.repository.TestCaseRepository;

/**
 * The final judgment write: one transaction updates the submission (status, verdict,
 * metrics) and inserts every {@code submission_results} row together — all or nothing
 * (PRD §13). Status catch-up covers attempts where an advisory progress marker was lost,
 * so the final write can never fail merely because an earlier cosmetic write did.
 */
@Service
public class SubmissionResultWriter {

    private static final int ERROR_MESSAGE_LIMIT = 2_000;

    private final SubmissionRepository submissionRepository;
    private final SubmissionResultRepository submissionResultRepository;
    private final TestCaseRepository testCaseRepository;

    public SubmissionResultWriter(SubmissionRepository submissionRepository,
                                  SubmissionResultRepository submissionResultRepository,
                                  TestCaseRepository testCaseRepository) {
        this.submissionRepository = submissionRepository;
        this.submissionResultRepository = submissionResultRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional
    public void writeCompilationError(long submissionId, String compilerOutput) {
        Submission submission = load(submissionId);
        advance(submission, SubmissionStatus.COMPILING);
        submission.applyTerminalOutcome(SubmissionStatus.COMPILATION_ERROR, Verdict.COMPILATION_ERROR,
                null, null, null, truncate(compilerOutput, ERROR_MESSAGE_LIMIT));
    }

    @Transactional
    public void writeJudged(JudgingOutcome outcome) {
        Submission submission = load(outcome.submissionId());
        advance(submission, SubmissionStatus.RUNNING);
        // The lifecycle graph (PRD §17) routes EVALUATING → COMPLETED only; failure
        // verdicts branch directly from RUNNING.
        if (outcome.terminalStatus() == SubmissionStatus.COMPLETED) {
            advance(submission, SubmissionStatus.EVALUATING);
        }

        TestCase failedTestCase = outcome.failedTestCaseId() == null
                ? null
                : testCaseRepository.getReferenceById(outcome.failedTestCaseId());
        submission.applyTerminalOutcome(outcome.terminalStatus(), outcome.verdict(),
                outcome.timeUsedMs(), outcome.memoryUsedKb(), failedTestCase, outcome.errorMessage());

        List<SubmissionResult> rows = outcome.results().stream()
                .map(caseOutcome -> new SubmissionResult(submission,
                        testCaseRepository.getReferenceById(caseOutcome.testCaseId()),
                        caseOutcome.verdict(), caseOutcome.timeUsedMs(), caseOutcome.memoryUsedKb(),
                        caseOutcome.stdoutSnippet()))
                .toList();
        submissionResultRepository.saveAll(rows);
    }

    private Submission load(long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Submission vanished during the final write: %d".formatted(submissionId)));
    }

    private static void advance(Submission submission, SubmissionStatus target) {
        if (submission.getStatus() != target && submission.getStatus().canTransitionTo(target)) {
            submission.transitionTo(target);
        }
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n[truncated]";
    }
}
