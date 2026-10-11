package com.onlinejudge.backend.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.ContestDetailResponse;
import com.onlinejudge.backend.api.dto.ContestProblemSummaryResponse;
import com.onlinejudge.backend.api.dto.ContestSummaryResponse;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.RegistrationResponse;
import com.onlinejudge.backend.exception.BusinessRuleException;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ContestParticipantRepository;
import com.onlinejudge.backend.repository.ContestProblemRepository;
import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.backend.repository.ContestSpecifications;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestParticipant;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.AppRole;
import com.onlinejudge.common.enums.ContestStatus;
import com.onlinejudge.common.enums.Verdict;

/**
 * Public contest listing/detail and participant registration (PRD §7.8). Registration is
 * additionally gated by {@code @PreAuthorize} at this layer as well as URL rules (AGENTS.md §10).
 */
@Service
@Transactional(readOnly = true)
public class ContestPublicServiceImpl implements ContestPublicService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ContestParticipantRepository contestParticipantRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    public ContestPublicServiceImpl(ContestRepository contestRepository,
                                    ContestProblemRepository contestProblemRepository,
                                    ContestParticipantRepository contestParticipantRepository,
                                    SubmissionRepository submissionRepository,
                                    UserRepository userRepository) {
        this.contestRepository = contestRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.contestParticipantRepository = contestParticipantRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
    }

    @Override
    public PagedResponse<ContestSummaryResponse> browse(ContestStatus status, int page, int size) {
        Instant now = Instant.now();
        Specification<Contest> spec = ContestSpecifications.publicOnly();
        if (status != null) {
            spec = spec.and(switch (status) {
                case UPCOMING -> ContestSpecifications.isUpcoming(now);
                case RUNNING -> ContestSpecifications.isRunning(now);
                case ENDED -> ContestSpecifications.isEnded(now);
            });
        }
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "startTime", "id"));
        Page<Contest> contests = contestRepository.findAll(spec, pageRequest);
        return PagedResponse.of(contests, contest -> new ContestSummaryResponse(contest.getId(),
                contest.getName(), contest.getStartTime(), contest.getEndTime(),
                ContestStatus.of(contest.getStartTime(), contest.getEndTime(), now),
                contestParticipantRepository.countByContestId(contest.getId()),
                contestProblemRepository.countByContestId(contest.getId())));
    }

    @Override
    public ContestDetailResponse detail(Long id, String usernameOrNull) {
        Instant now = Instant.now();
        Contest contest = visibleContest(id, usernameOrNull);
        ContestStatus status = ContestStatus.of(contest.getStartTime(), contest.getEndTime(), now);
        User caller = callerOrNull(usernameOrNull);
        boolean isAdmin = isAdmin(caller);
        boolean registered = caller != null
                && contestParticipantRepository.existsByContestIdAndUserId(id, caller.getId());
        boolean problemsRevealed = isAdmin || status != ContestStatus.UPCOMING;
        List<ContestProblemSummaryResponse> problems = problemsRevealed ? problems(id, caller) : null;
        return new ContestDetailResponse(contest.getId(), contest.getName(), contest.getDescription(),
                contest.getStartTime(), contest.getEndTime(), status, contest.getFreezeMinutes(),
                contestParticipantRepository.countByContestId(id), registered,
                !contest.isPrivate() && status == ContestStatus.UPCOMING, problemsRevealed, problems);
    }

    @Override
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public RegistrationResponse register(Long id, String username) {
        Contest contest = visibleContest(id, username);
        if (contest.isPrivate()) {
            throw new ResourceNotFoundException("Contest not found: " + id);
        }
        requireUpcoming(contest);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User does not exist"));
        if (!contestParticipantRepository.existsByContestIdAndUserId(id, user.getId())) {
            contestParticipantRepository.save(new ContestParticipant(contest, user));
        }
        return new RegistrationResponse(true);
    }

    @Override
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void unregister(Long id, String username) {
        Contest contest = visibleContest(id, username);
        if (contest.isPrivate()) {
            throw new ResourceNotFoundException("Contest not found: " + id);
        }
        requireUpcoming(contest);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User does not exist"));
        contestParticipantRepository.findByContestId(id).stream()
                .filter(participant -> participant.getUser().getId().equals(user.getId()))
                .findFirst()
                .ifPresent(contestParticipantRepository::delete);
    }

    private void requireUpcoming(Contest contest) {
        if (ContestStatus.of(contest.getStartTime(), contest.getEndTime(), Instant.now())
                != ContestStatus.UPCOMING) {
            throw new BusinessRuleException("Registration is closed for this contest");
        }
    }

    private Contest visibleContest(Long id, String usernameOrNull) {
        Contest contest = contestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found: " + id));
        if (contest.isPrivate() && !isAdmin(callerOrNull(usernameOrNull))) {
            throw new ResourceNotFoundException("Contest not found: " + id);
        }
        return contest;
    }

    private User callerOrNull(String usernameOrNull) {
        return usernameOrNull == null ? null : userRepository.findByUsername(usernameOrNull).orElse(null);
    }

    static boolean isAdmin(User user) {
        return user != null && user.getRoles().stream()
                .anyMatch(role -> AppRole.ROLE_ADMIN.name().equals(role.getName()));
    }

    private List<ContestProblemSummaryResponse> problems(Long contestId, User caller) {
        List<ContestProblem> links = contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(contestId);
        Set<Long> solved = Set.of();
        if (caller != null && !links.isEmpty()) {
            List<Long> problemIds = links.stream().map(link -> link.getProblem().getId()).toList();
            solved = new HashSet<>(submissionRepository.findProblemIdsSolvedBy(
                    caller.getId(), problemIds, Verdict.ACCEPTED));
        }
        Set<Long> solvedIds = solved;
        return links.stream().map(link -> new ContestProblemSummaryResponse(link.getProblem().getId(),
                link.getProblem().getSlug(), link.getProblem().getTitle(), link.getProblem().getDifficulty(),
                link.getDisplayOrder(), link.getPoints(),
                solvedIds.contains(link.getProblem().getId()))).toList();
    }

    private static int clampSize(int size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }
}
