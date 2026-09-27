package com.onlinejudge.worker.consumer;

import java.net.InetAddress;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;
import com.onlinejudge.worker.config.WorkerProperties;
import com.onlinejudge.worker.repository.ExecutionJobRepository;
import com.onlinejudge.worker.repository.SubmissionRepository;

/**
 * Lease-based job claiming (PRD §12/§17). The lease is the deduplication point: exactly
 * one worker wins a job; every other delivery of the same message is a no-op. Retries are
 * bounded by {@code max_retries} — exhausted jobs become terminal SYSTEM_ERROR.
 */
@Service
public class JobClaimService {

    private static final Logger log = LoggerFactory.getLogger(JobClaimService.class);

    public enum ClaimResult {
        CLAIMED,
        DUPLICATE,
        ALREADY_TERMINAL,
        RETRIES_EXHAUSTED
    }

    private final ExecutionJobRepository jobRepository;
    private final SubmissionRepository submissionRepository;
    private final WorkerProperties properties;
    private final String workerId = defaultWorkerId();

    public JobClaimService(ExecutionJobRepository jobRepository, SubmissionRepository submissionRepository,
                           WorkerProperties properties) {
        this.jobRepository = jobRepository;
        this.submissionRepository = submissionRepository;
        this.properties = properties;
    }

    @Transactional
    public ClaimResult claim(long submissionId) {
        int acquired = jobRepository.tryAcquireLease(submissionId, workerId,
                properties.leaseDuration().toMillis() / 1000.0);
        if (acquired == 0) {
            return ClaimResult.DUPLICATE;
        }

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Execution job exists without submission %d".formatted(submissionId)));

        // A duplicate delivery may arrive after judging finished: ack without redoing work (PRD §17).
        if (submission.getStatus().isTerminal()) {
            log.info("Submission {} is already terminal ({}); discarding duplicate delivery",
                    submissionId, submission.getStatus());
            return ClaimResult.ALREADY_TERMINAL;
        }

        ExecutionJob job = jobRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Execution job vanished for submission %d".formatted(submissionId)));
        if (job.getRetryCount() > job.getMaxRetries()) {
            log.warn("Submission {} exhausted retries ({} attempts > {}); marking SYSTEM_ERROR",
                    submissionId, job.getRetryCount(), job.getMaxRetries());
            submission.applyTerminalOutcome(SubmissionStatus.SYSTEM_ERROR, Verdict.SYSTEM_ERROR,
                    null, null, null, "Retries exhausted");
            return ClaimResult.RETRIES_EXHAUSTED;
        }

        advanceToPickedUp(submission, submissionId);
        return ClaimResult.CLAIMED;
    }

    /** Gives the lease back after a failed attempt so the retry is not mistaken for a duplicate. */
    @Transactional
    public void release(long submissionId) {
        jobRepository.releaseLease(submissionId, workerId);
    }

    /**
     * Re-entry after a crashed attempt can find the submission mid-flight; only advance a
     * submission that has not started yet, never regress one that is already in progress
     * (statuses stay monotonic, PRD §8).
     */
    private void advanceToPickedUp(Submission submission, long submissionId) {
        switch (submission.getStatus()) {
            case SUBMITTED -> {
                // Publish succeeded but the QUEUED flip was lost; the message proves it is queued.
                submission.transitionTo(SubmissionStatus.QUEUED);
                submission.transitionTo(SubmissionStatus.PICKED_UP);
            }
            case QUEUED -> submission.transitionTo(SubmissionStatus.PICKED_UP);
            default -> log.info("Re-entering submission {} at stage {}", submissionId, submission.getStatus());
        }
    }

    private static String defaultWorkerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            // Swallowing is correct because a fallback identity is still unique enough
            // for lease ownership; hostname resolution must never block startup.
            host = "worker";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
