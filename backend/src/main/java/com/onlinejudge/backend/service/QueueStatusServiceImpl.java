package com.onlinejudge.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.QueueStatusResponse;
import com.onlinejudge.backend.repository.ExecutionJobRepository;
import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.enums.SubmissionStatus;

/**
 * Authorization is enforced here as well as at the controller (AGENTS.md §10). Values are
 * derived from the lease table only; the broker's queue depth is deliberately not read.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN')")
public class QueueStatusServiceImpl implements QueueStatusService {

    private static final int STALE_JOB_LIMIT = 50;
    private static final List<SubmissionStatus> QUEUED =
            List.of(SubmissionStatus.SUBMITTED, SubmissionStatus.QUEUED);
    private static final List<SubmissionStatus> TERMINAL = Arrays.stream(SubmissionStatus.values())
            .filter(SubmissionStatus::isTerminal)
            .toList();

    private final ExecutionJobRepository executionJobRepository;

    public QueueStatusServiceImpl(ExecutionJobRepository executionJobRepository) {
        this.executionJobRepository = executionJobRepository;
    }

    @Override
    public QueueStatusResponse current() {
        Instant now = Instant.now();
        long queued = executionJobRepository.countBySubmissionStatusIn(QUEUED);
        long active = executionJobRepository.countActiveLeases(now, TERMINAL);
        long stale = executionJobRepository.countStaleLeases(now, TERMINAL);
        long retried = executionJobRepository.countRetried();
        Instant oldest = executionJobRepository.findOldestQueuedCreatedAt(QUEUED);
        Long oldestAge = oldest == null ? null : Duration.between(oldest, now).toSeconds();

        List<QueueStatusResponse.StaleJob> staleJobs = executionJobRepository
                .findStaleLeases(now, TERMINAL, PageRequest.of(0, STALE_JOB_LIMIT)).stream()
                .map(job -> toStaleJob(job, now))
                .toList();

        return new QueueStatusResponse(queued, active, stale, retried, oldestAge, staleJobs);
    }

    private static QueueStatusResponse.StaleJob toStaleJob(ExecutionJob job, Instant now) {
        return new QueueStatusResponse.StaleJob(
                job.getSubmission().getId(),
                job.getSubmission().getStatus(),
                job.getLockedBy(),
                job.getLeaseExpiresAt(),
                job.getRetryCount(),
                Duration.between(job.getLeaseExpiresAt(), now).toSeconds());
    }
}
