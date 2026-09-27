package com.onlinejudge.worker.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;
import com.onlinejudge.worker.repository.SubmissionRepository;

/**
 * PHASE 7 PLACEHOLDER: the no-op judge. It exists to prove the full pipeline
 * (queue → lease → status transitions → terminal write) before any container runs.
 * Phase 8/9 replace it with the Docker sandbox and the real verdict engine — do not grow
 * judging logic here.
 *
 * <p>The final write is one transaction (PRD §13): all status transitions and the
 * terminal outcome commit together or not at all, so a crash can never persist a
 * half-judged submission.
 */
@Service
public class StubJudgeService {

    private static final Logger log = LoggerFactory.getLogger(StubJudgeService.class);

    private final SubmissionRepository submissionRepository;

    public StubJudgeService(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public void judge(SubmissionJobMessage message) {
        Submission submission = submissionRepository.findById(message.submissionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Submission vanished during judging: %d".formatted(message.submissionId())));
        submission.transitionTo(SubmissionStatus.RUNNING);
        submission.transitionTo(SubmissionStatus.EVALUATING);
        submission.applyTerminalOutcome(SubmissionStatus.COMPLETED, Verdict.ACCEPTED, null, null, null, null);
        log.info("Stub-judged submission {} as ACCEPTED ({} test cases to run in Phase 9)",
                message.submissionId(), message.testCaseIds().size());
    }
}
