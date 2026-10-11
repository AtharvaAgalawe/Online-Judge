package com.onlinejudge.backend.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.ContestProblemDetailResponse;
import com.onlinejudge.backend.api.dto.SampleTestCaseResponse;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ContestParticipantRepository;
import com.onlinejudge.backend.repository.ContestProblemRepository;
import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.AppRole;
import com.onlinejudge.common.enums.ContestStatus;

/**
 * Contest problem statement access (PRD §7.8). Non-admin callers may read a problem only while the
 * contest is RUNNING and they are registered, or after it ENDED and the problem is published;
 * admins bypass both gates. Every denial is indistinguishable from a nonexistent resource.
 */
@Service
@Transactional(readOnly = true)
public class ContestProblemServiceImpl implements ContestProblemService {

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ContestParticipantRepository contestParticipantRepository;
    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final UserRepository userRepository;

    public ContestProblemServiceImpl(ContestRepository contestRepository,
                                     ContestProblemRepository contestProblemRepository,
                                     ContestParticipantRepository contestParticipantRepository,
                                     ProblemRepository problemRepository,
                                     TestCaseRepository testCaseRepository,
                                     UserRepository userRepository) {
        this.contestRepository = contestRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.contestParticipantRepository = contestParticipantRepository;
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ContestProblemDetailResponse detail(Long contestId, String slug, String usernameOrNull) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + slug));
        User caller = usernameOrNull == null ? null
                : userRepository.findByUsername(usernameOrNull).orElse(null);
        boolean isAdmin = caller != null && caller.getRoles().stream()
                .anyMatch(role -> AppRole.ROLE_ADMIN.name().equals(role.getName()));
        if (contest.isPrivate() && !isAdmin) {
            throw new ResourceNotFoundException("Problem not found: " + slug);
        }
        Problem problem = problemRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + slug));
        ContestProblem link = contestProblemRepository.findByProblemId(problem.getId())
                .filter(candidate -> candidate.getContest().getId().equals(contestId))
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + slug));

        if (!isAdmin) {
            ContestStatus status = ContestStatus.of(contest.getStartTime(), contest.getEndTime(), Instant.now());
            boolean registered = caller != null
                    && contestParticipantRepository.existsByContestIdAndUserId(contestId, caller.getId());
            boolean allowed = (status == ContestStatus.RUNNING && registered)
                    || (status == ContestStatus.ENDED && problem.isPublished());
            if (!allowed) {
                throw new ResourceNotFoundException("Problem not found: " + slug);
            }
        }

        List<SampleTestCaseResponse> samples = testCaseRepository
                .findByProblemIdAndSampleTrueOrderByDisplayOrderAsc(problem.getId()).stream()
                .map(SampleTestCaseResponse::from)
                .toList();
        return new ContestProblemDetailResponse(problem.getId(), problem.getSlug(), problem.getTitle(),
                problem.getStatement(), problem.getDifficulty(), problem.getTimeLimitMs(),
                problem.getMemoryLimitKb(), link.getPoints(), contest.getId(), contest.getName(), samples);
    }
}
