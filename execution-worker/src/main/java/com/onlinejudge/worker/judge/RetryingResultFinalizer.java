package com.onlinejudge.worker.judge;

import org.springframework.stereotype.Service;

import io.github.resilience4j.retry.annotation.Retry;

/**
 * Bounded in-process retry (PRD §23) around the final transactional write: transient
 * database blips are absorbed here, while anything that still fails propagates to the
 * listener's queue-level retry path. The retry sits outside the transaction (a fresh
 * attempt starts a fresh transaction) and in its own bean so the proxy applies.
 */
@Service
public class RetryingResultFinalizer {

    private final SubmissionResultWriter writer;

    public RetryingResultFinalizer(SubmissionResultWriter writer) {
        this.writer = writer;
    }

    @Retry(name = "resultWrite")
    public void finalizeCompilationError(long submissionId, String compilerOutput) {
        writer.writeCompilationError(submissionId, compilerOutput);
    }

    @Retry(name = "resultWrite")
    public void finalizeJudged(JudgingOutcome outcome) {
        writer.writeJudged(outcome);
    }
}
