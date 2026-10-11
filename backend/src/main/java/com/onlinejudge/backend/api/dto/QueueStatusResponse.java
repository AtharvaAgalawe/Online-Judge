package com.onlinejudge.backend.api.dto;

import java.time.Instant;
import java.util.List;

import com.onlinejudge.common.enums.SubmissionStatus;

/**
 * Admin queue-health snapshot (PRD §7.7). Derived solely from the database
 * ({@code execution_jobs} + {@code submissions}) — it is NOT the RabbitMQ broker's
 * queue depth (AGENTS.md §11/§14: do not oversell what is measured).
 */
public record QueueStatusResponse(
        long queued,
        long activeLease,
        long staleLease,
        long retried,
        Long oldestQueuedAgeSeconds,
        List<StaleJob> stale) {

    /** A job whose lease has expired while its submission is not terminal. */
    public record StaleJob(
            Long submissionId,
            SubmissionStatus status,
            String lockedBy,
            Instant leaseExpiresAt,
            int retryCount,
            long ageSeconds) {
    }
}
