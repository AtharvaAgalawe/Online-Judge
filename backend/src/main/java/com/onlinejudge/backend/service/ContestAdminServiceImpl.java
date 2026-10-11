package com.onlinejudge.backend.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.AttachContestProblemRequest;
import com.onlinejudge.backend.api.dto.AttachableProblemResponse;
import com.onlinejudge.backend.api.dto.ContestAdminDetailResponse;
import com.onlinejudge.backend.api.dto.ContestAdminSummaryResponse;
import com.onlinejudge.backend.api.dto.ContestCreatedResponse;
import com.onlinejudge.backend.api.dto.ContestProblemCreatedResponse;
import com.onlinejudge.backend.api.dto.CreateContestProblemRequest;
import com.onlinejudge.backend.api.dto.CreateContestRequest;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.UpdateContestProblemRequest;
import com.onlinejudge.backend.api.dto.UpdateContestRequest;
import com.onlinejudge.backend.exception.BusinessRuleException;
import com.onlinejudge.backend.exception.DuplicateResourceException;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ContestParticipantRepository;
import com.onlinejudge.backend.repository.ContestProblemRepository;
import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.backend.repository.ContestSpecifications;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.ProblemSpecifications;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.ContestStatus;

/**
 * Authorization is enforced here as well as at the controller (AGENTS.md §10 defense in
 * depth): a controller reorganization must not silently drop the admin gate.
 */
@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class ContestAdminServiceImpl implements ContestAdminService {

    private static final int DEFAULT_POINTS = 100;
    private static final int MAX_ATTACHABLE_RESULTS = 200;

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ContestParticipantRepository contestParticipantRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;

    public ContestAdminServiceImpl(ContestRepository contestRepository,
                                   ContestProblemRepository contestProblemRepository,
                                   ContestParticipantRepository contestParticipantRepository,
                                   ProblemRepository problemRepository,
                                   UserRepository userRepository) {
        this.contestRepository = contestRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.contestParticipantRepository = contestParticipantRepository;
        this.problemRepository = problemRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ContestAdminSummaryResponse> list(String search, ContestStatus status, int page, int size) {
        Instant now = Instant.now();
        // Specification.where(null) is deprecated for removal since Spring Data JPA 3.5;
        // a conjunction predicate is the equivalent supported no-op start.
        Specification<Contest> spec = (root, query, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            spec = spec.and(ContestSpecifications.nameContains(search));
        }
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
        return PagedResponse.of(contests, contest -> toSummary(contest, now));
    }

    @Override
    @Transactional(readOnly = true)
    public ContestAdminDetailResponse detail(Long id) {
        Contest contest = contestById(id);
        return new ContestAdminDetailResponse(contest.getId(), contest.getName(), contest.getDescription(),
                contest.getStartTime(), contest.getEndTime(), contest.isPrivate(), contest.getFreezeMinutes(),
                ContestStatus.of(contest.getStartTime(), contest.getEndTime(), Instant.now()),
                contest.getProblemsPublishedAt());
    }

    @Override
    public ContestCreatedResponse create(CreateContestRequest request, String adminUsername) {
        validateWindow(request.startTime(), request.endTime());
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User does not exist"));
        Contest contest = new Contest(request.name(), request.description(), request.startTime(),
                request.endTime(), request.isPrivate(),
                request.freezeMinutes() == null ? 60 : request.freezeMinutes(), admin);
        contestRepository.saveAndFlush(contest);
        return new ContestCreatedResponse(contest.getId(), contest.getName());
    }

    @Override
    public void update(Long id, UpdateContestRequest request) {
        validateWindow(request.startTime(), request.endTime());
        Contest contest = contestById(id);
        requireNotStarted(contest, "edited");
        contest.updateMetadata(request.name(), request.description(), request.startTime(),
                request.endTime(), request.isPrivate(),
                request.freezeMinutes() == null ? contest.getFreezeMinutes() : request.freezeMinutes());
        contestRepository.save(contest);
    }

    @Override
    public void delete(Long id) {
        Contest contest = contestById(id);
        requireNotStarted(contest, "deleted");
        contestRepository.delete(contest);
    }

    @Override
    public void attachProblem(Long id, AttachContestProblemRequest request) {
        Contest contest = contestById(id);
        requireNotStarted(contest, "edited");
        Problem problem = problemRepository.findById(request.problemId())
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + request.problemId()));
        if (problem.isPublished()) {
            throw new BusinessRuleException("Only unpublished problems can be attached to a contest");
        }
        if (contestProblemRepository.findByProblemId(problem.getId()).isPresent()) {
            throw new DuplicateResourceException("Problem is already attached to a contest");
        }
        contestProblemRepository.save(new ContestProblem(contest, problem,
                request.points() == null ? DEFAULT_POINTS : request.points(), nextOrder(id)));
    }

    @Override
    public ContestProblemCreatedResponse createProblem(Long id, CreateContestProblemRequest request,
                                                       String adminUsername) {
        Contest contest = contestById(id);
        requireNotStarted(contest, "edited");
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User does not exist"));
        String slug = SlugGenerator.unique(SlugGenerator.slugify(request.title()), problemRepository::existsBySlug);
        Problem problem = new Problem(slug, request.title(), request.statement(), request.difficulty(),
                request.timeLimitMs() == null ? 2000 : request.timeLimitMs(),
                request.memoryLimitKb() == null ? 262_144 : request.memoryLimitKb(), admin);
        problemRepository.saveAndFlush(problem);
        contestProblemRepository.save(new ContestProblem(contest, problem,
                request.points() == null ? DEFAULT_POINTS : request.points(), nextOrder(id)));
        return new ContestProblemCreatedResponse(problem.getId(), problem.getSlug());
    }

    @Override
    public void updateProblem(Long id, Long problemId, UpdateContestProblemRequest request) {
        Contest contest = contestById(id);
        requireNotStarted(contest, "edited");
        ContestProblem link = linkOf(contest, problemId);
        if (request.points() != null) {
            link.setPoints(request.points());
        }
        if (request.displayOrder() != null) {
            link.setDisplayOrder(request.displayOrder());
        }
        contestProblemRepository.save(link);
    }

    @Override
    public void detachProblem(Long id, Long problemId) {
        Contest contest = contestById(id);
        requireNotStarted(contest, "edited");
        contestProblemRepository.delete(linkOf(contest, problemId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachableProblemResponse> listAttachableProblems(String search) {
        Set<Long> attached = new HashSet<>(contestProblemRepository.findAllProblemIds());
        return problemRepository.findAll(ProblemSpecifications.hasPublished(false)).stream()
                .filter(problem -> !attached.contains(problem.getId()))
                .filter(problem -> search == null || search.isBlank()
                        || problem.getTitle().toLowerCase(Locale.ROOT)
                                .contains(search.toLowerCase(Locale.ROOT)))
                .limit(MAX_ATTACHABLE_RESULTS)
                .map(AttachableProblemResponse::from)
                .toList();
    }

    private ContestAdminSummaryResponse toSummary(Contest contest, Instant now) {
        return new ContestAdminSummaryResponse(contest.getId(), contest.getName(), contest.getStartTime(),
                contest.getEndTime(), ContestStatus.of(contest.getStartTime(), contest.getEndTime(), now),
                contest.isPrivate(), contestParticipantRepository.countByContestId(contest.getId()),
                contestProblemRepository.countByContestId(contest.getId()), contest.getProblemsPublishedAt());
    }

    private int nextOrder(Long contestId) {
        return contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(contestId).stream()
                .mapToInt(ContestProblem::getDisplayOrder).max().orElse(-1) + 1;
    }

    private void validateWindow(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("Contest end time must be after start time");
        }
    }

    private Contest contestById(Long id) {
        return contestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found: " + id));
    }

    private void requireNotStarted(Contest contest, String verb) {
        if (ContestStatus.of(contest.getStartTime(), contest.getEndTime(), Instant.now())
                != ContestStatus.UPCOMING) {
            throw new BusinessRuleException("Contest can no longer be " + verb + " after it has started");
        }
    }

    private ContestProblem linkOf(Contest contest, Long problemId) {
        return contestProblemRepository.findByProblemId(problemId)
                .filter(link -> link.getContest().getId().equals(contest.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem " + problemId + " is not attached to contest " + contest.getId()));
    }

    private static int clampSize(int size) {
        return size <= 0 ? 20 : Math.min(size, 100);
    }
}
