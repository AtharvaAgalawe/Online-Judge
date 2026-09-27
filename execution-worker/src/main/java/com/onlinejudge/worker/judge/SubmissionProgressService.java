package com.onlinejudge.worker.judge;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.worker.repository.SubmissionRepository;

/**
 * Advisory progress markers so pollers observe COMPILING/RUNNING while judging proceeds.
 * They are deliberately small separate transactions: the authoritative write is the one
 * final transaction (PRD §13), and a failed progress marker must never fail the job —
 * callers log and continue.
 */
@Service
public class SubmissionProgressService {

    private final SubmissionRepository submissionRepository;

    public SubmissionProgressService(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public void markCompiling(long submissionId) {
        advance(submissionId, SubmissionStatus.COMPILING);
    }

    @Transactional
    public void markRunning(long submissionId) {
        advance(submissionId, SubmissionStatus.RUNNING);
    }

    private void advance(long submissionId, SubmissionStatus target) {
        submissionRepository.findById(submissionId).ifPresent(submission -> {
            if (submission.getStatus() != target && submission.getStatus().canTransitionTo(target)) {
                submission.transitionTo(target);
            }
        });
    }
}
