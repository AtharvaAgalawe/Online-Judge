package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.onlinejudge.backend.repository.ExecutionJobRepository;
import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.SubmissionStatus;

@ExtendWith(MockitoExtension.class)
class QueueStatusServiceImplTest {

    @Mock
    private ExecutionJobRepository executionJobRepository;

    private QueueStatusServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QueueStatusServiceImpl(executionJobRepository);
    }

    @Test
    void reportsCountsAndStaleJobs() {
        when(executionJobRepository.countBySubmissionStatusIn(any())).thenReturn(4L);
        when(executionJobRepository.countActiveLeases(any(), any())).thenReturn(2L);
        when(executionJobRepository.countStaleLeases(any(), any())).thenReturn(1L);
        when(executionJobRepository.countRetried()).thenReturn(3L);
        when(executionJobRepository.findOldestQueuedCreatedAt(any()))
                .thenReturn(Instant.now().minusSeconds(30));
        ExecutionJob job = staleJob();
        when(executionJobRepository.findStaleLeases(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(job));

        var status = service.current();

        assertThat(status.queued()).isEqualTo(4);
        assertThat(status.activeLease()).isEqualTo(2);
        assertThat(status.staleLease()).isEqualTo(1);
        assertThat(status.retried()).isEqualTo(3);
        assertThat(status.oldestQueuedAgeSeconds()).isNotNull();
        assertThat(status.stale()).hasSize(1);
        assertThat(status.stale().get(0).lockedBy()).isEqualTo("worker-1");
        assertThat(status.stale().get(0).status()).isEqualTo(SubmissionStatus.RUNNING);
    }

    @Test
    void oldestAgeIsNullWhenNothingIsQueued() {
        when(executionJobRepository.findOldestQueuedCreatedAt(any())).thenReturn(null);
        when(executionJobRepository.findStaleLeases(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        var status = service.current();

        assertThat(status.oldestQueuedAgeSeconds()).isNull();
        assertThat(status.stale()).isEmpty();
    }

    private ExecutionJob staleJob() {
        User user = new User("u", "u@x.com", "h");
        Problem problem = new Problem("slug", "Title", "s", Difficulty.EASY, 1000, 65536, null);
        Submission submission = new Submission(user, problem,
                new Language("Java 21", "Main.java", "javac", "java", "oj-java21", java.math.BigDecimal.ONE),
                "code", null);
        submission.transitionTo(SubmissionStatus.QUEUED);
        submission.transitionTo(SubmissionStatus.PICKED_UP);
        submission.transitionTo(SubmissionStatus.RUNNING);
        ExecutionJob job = new ExecutionJob(submission);
        org.springframework.test.util.ReflectionTestUtils.setField(job, "lockedBy", "worker-1");
        org.springframework.test.util.ReflectionTestUtils.setField(job, "lockedAt", Instant.now().minusSeconds(120));
        org.springframework.test.util.ReflectionTestUtils.setField(
                job, "leaseExpiresAt", Instant.now().minusSeconds(60));
        return job;
    }
}
