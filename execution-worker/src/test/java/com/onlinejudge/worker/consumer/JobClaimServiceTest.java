package com.onlinejudge.worker.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;
import com.onlinejudge.worker.config.WorkerProperties;
import com.onlinejudge.worker.repository.ExecutionJobRepository;
import com.onlinejudge.worker.repository.SubmissionRepository;

@ExtendWith(MockitoExtension.class)
class JobClaimServiceTest {

    private static final long SUBMISSION_ID = 42L;

    @Mock
    private ExecutionJobRepository jobRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    private JobClaimService claimService;

    @BeforeEach
    void setUp() {
        claimService = new JobClaimService(jobRepository, submissionRepository,
                new WorkerProperties(Duration.ofSeconds(60)));
    }

    @Test
    void losingTheLeaseIsADuplicateAndNeverTouchesTheSubmission() {
        when(jobRepository.tryAcquireLease(eq(SUBMISSION_ID), anyString(), anyDouble())).thenReturn(0);

        JobClaimService.ClaimResult result = claimService.claim(SUBMISSION_ID);

        assertThat(result).isEqualTo(JobClaimService.ClaimResult.DUPLICATE);
        verifyNoInteractions(submissionRepository);
    }

    @Test
    void terminalSubmissionIsReportedWithoutStatusChanges() {
        when(jobRepository.tryAcquireLease(eq(SUBMISSION_ID), anyString(), anyDouble())).thenReturn(1);
        Submission submission = submissionInStatus(SubmissionStatus.COMPLETED);
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));

        JobClaimService.ClaimResult result = claimService.claim(SUBMISSION_ID);

        assertThat(result).isEqualTo(JobClaimService.ClaimResult.ALREADY_TERMINAL);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.COMPLETED);
    }

    @Test
    void exhaustedRetriesBecomeTerminalSystemError() {
        when(jobRepository.tryAcquireLease(eq(SUBMISSION_ID), anyString(), anyDouble())).thenReturn(1);
        Submission submission = submissionInStatus(SubmissionStatus.QUEUED);
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        ExecutionJob job = new ExecutionJob(submission);
        ReflectionTestUtils.setField(job, "retryCount", 4);
        when(jobRepository.findBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(job));

        JobClaimService.ClaimResult result = claimService.claim(SUBMISSION_ID);

        assertThat(result).isEqualTo(JobClaimService.ClaimResult.RETRIES_EXHAUSTED);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        assertThat(submission.getVerdict()).isEqualTo(Verdict.SYSTEM_ERROR);
        assertThat(submission.getJudgedAt()).isNotNull();
    }

    @Test
    void queuedSubmissionAdvancesToPickedUp() {
        when(jobRepository.tryAcquireLease(eq(SUBMISSION_ID), anyString(), anyDouble())).thenReturn(1);
        Submission submission = submissionInStatus(SubmissionStatus.QUEUED);
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        stubJob(submission);

        JobClaimService.ClaimResult result = claimService.claim(SUBMISSION_ID);

        assertThat(result).isEqualTo(JobClaimService.ClaimResult.CLAIMED);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.PICKED_UP);
    }

    @Test
    void submittedSubmissionCatchesUpThroughQueuedToPickedUp() {
        when(jobRepository.tryAcquireLease(eq(SUBMISSION_ID), anyString(), anyDouble())).thenReturn(1);
        Submission submission = new Submission(null, null, null, "code", null);
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        stubJob(submission);

        JobClaimService.ClaimResult result = claimService.claim(SUBMISSION_ID);

        assertThat(result).isEqualTo(JobClaimService.ClaimResult.CLAIMED);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.PICKED_UP);
    }

    @Test
    void reentryAfterCrashKeepsTheInFlightStatus() {
        when(jobRepository.tryAcquireLease(eq(SUBMISSION_ID), anyString(), anyDouble())).thenReturn(1);
        Submission submission = submissionInStatus(SubmissionStatus.RUNNING);
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        stubJob(submission);

        JobClaimService.ClaimResult result = claimService.claim(SUBMISSION_ID);

        assertThat(result).isEqualTo(JobClaimService.ClaimResult.CLAIMED);
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.RUNNING);
    }

    @Test
    void releaseForwardsToTheRepository() {
        claimService.release(SUBMISSION_ID);

        verify(jobRepository).releaseLease(eq(SUBMISSION_ID), anyString());
    }

    private void stubJob(Submission submission) {
        when(jobRepository.findBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(new ExecutionJob(submission)));
    }

    private Submission submissionInStatus(SubmissionStatus status) {
        Submission submission = new Submission(null, null, null, "code", null);
        SubmissionStatus[] path = {SubmissionStatus.QUEUED, SubmissionStatus.PICKED_UP,
                SubmissionStatus.COMPILING, SubmissionStatus.RUNNING, SubmissionStatus.EVALUATING,
                SubmissionStatus.COMPLETED};
        for (SubmissionStatus step : path) {
            if (submission.getStatus() == status) {
                break;
            }
            submission.transitionTo(step);
        }
        return submission;
    }
}

