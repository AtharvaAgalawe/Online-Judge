package com.onlinejudge.backend.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.repository.ContestProblemRepository;
import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.Problem;

/**
 * Publishes the problems of one ended public contest. Called by the scheduler; a separate
 * bean so each contest gets its own transaction (one bad contest cannot block the rest)
 * and so tests can invoke it directly (PRD §7.8).
 */
@Service
public class ContestPublicationService {

    private static final Logger log = LoggerFactory.getLogger(ContestPublicationService.class);

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProblemRepository problemRepository;

    public ContestPublicationService(ContestRepository contestRepository,
                                     ContestProblemRepository contestProblemRepository,
                                     TestCaseRepository testCaseRepository,
                                     ProblemRepository problemRepository) {
        this.contestRepository = contestRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.testCaseRepository = testCaseRepository;
        this.problemRepository = problemRepository;
    }

    /**
     * Publishes every still-unpublished problem of the contest that has test cases, then marks
     * the contest as processed. The marker makes the pass idempotent: rerunning after a partial
     * failure only publishes what is still missing. Problems without test cases are left
     * unpublished (with a WARN) so publishing never creates an unjudgeable problem.
     */
    @Transactional
    public void publishContest(Long contestId) {
        Contest contest = contestRepository.findById(contestId).orElse(null);
        if (contest == null || contest.isPrivate() || contest.getProblemsPublishedAt() != null) {
            return;
        }
        if (contest.getEndTime().isAfter(Instant.now())) {
            return;
        }
        for (ContestProblem link : contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(contestId)) {
            Problem problem = link.getProblem();
            if (problem.isPublished()) {
                continue;
            }
            if (testCaseRepository.countByProblemId(problem.getId()) == 0) {
                log.warn("Contest {} ended but problem {} has no test cases; leaving unpublished",
                        contestId, problem.getId());
                continue;
            }
            problem.setPublished(true);
            problemRepository.save(problem);
            log.info("Published contest problem {} after contest {} ended", problem.getId(), contestId);
        }
        contest.markProblemsPublished(Instant.now());
        contestRepository.save(contest);
    }
}
