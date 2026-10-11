package com.onlinejudge.backend.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.config.SubmissionProperties;
import com.onlinejudge.backend.exception.BusinessRuleException;
import com.onlinejudge.backend.exception.ContestRegistrationRequiredException;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ContestParticipantRepository;
import com.onlinejudge.backend.repository.ContestProblemRepository;
import com.onlinejudge.backend.repository.ExecutionJobRepository;
import com.onlinejudge.backend.repository.LanguageRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.TestCase;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.AppRole;
import com.onlinejudge.common.enums.ContestStatus;

/**
 * The transactional core of submission creation: submission row + execution job row commit
 * together or not at all (PRD §13 transaction boundaries). The job message is built inside
 * this transaction and published as a domain event; the transport send happens only after
 * commit ({@link com.onlinejudge.backend.messaging.SubmissionJobPublisher}).
 */
@Service
@Transactional
public class SubmissionCreator {

    private final SubmissionRepository submissionRepository;
    private final ExecutionJobRepository executionJobRepository;
    private final ProblemRepository problemRepository;
    private final LanguageRepository languageRepository;
    private final UserRepository userRepository;
    private final TestCaseRepository testCaseRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ContestParticipantRepository contestParticipantRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SubmissionProperties properties;

    public SubmissionCreator(SubmissionRepository submissionRepository, ExecutionJobRepository executionJobRepository,
                             ProblemRepository problemRepository, LanguageRepository languageRepository,
                             UserRepository userRepository, TestCaseRepository testCaseRepository,
                             ContestProblemRepository contestProblemRepository,
                             ContestParticipantRepository contestParticipantRepository,
                             ApplicationEventPublisher eventPublisher, SubmissionProperties properties) {
        this.submissionRepository = submissionRepository;
        this.executionJobRepository = executionJobRepository;
        this.problemRepository = problemRepository;
        this.languageRepository = languageRepository;
        this.userRepository = userRepository;
        this.testCaseRepository = testCaseRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.contestParticipantRepository = contestParticipantRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    public Submission create(Long userId, Long problemId, Long languageId, String sourceCode,
                             String idempotencyKey, String correlationId) {
        int sourceBytes = sourceCode.getBytes(StandardCharsets.UTF_8).length;
        if (sourceBytes > properties.maxSourceBytes()) {
            throw new BusinessRuleException(
                    "Source code exceeds the %d byte limit".formatted(properties.maxSourceBytes()));
        }

        // Unpublished problems are indistinguishable from missing ones to solvers (PRD §7.2),
        // except while a contest containing the problem is running (PRD §7.8).
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + problemId));
        if (!problem.isPublished()) {
            requireContestAccess(problem, userId);
        }
        Language language = languageRepository.findById(languageId)
                .filter(Language::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Language not found: " + languageId));
        User user = userRepository.getReferenceById(userId);

        Submission submission = new Submission(user, problem, language, sourceCode, idempotencyKey);
        submissionRepository.saveAndFlush(submission);
        executionJobRepository.save(new ExecutionJob(submission));

        eventPublisher.publishEvent(new SubmissionCreatedEvent(
                buildJobMessage(submission, problem, language), correlationId));
        return submission;
    }

    /**
     * A problem hidden by its contest is submittable only while that contest runs and only
     * by its registered participants (admins bypass for testing). Anything else is 404 so
     * private/ended contests stay indistinguishable from missing problems.
     */
    private void requireContestAccess(Problem problem, Long userId) {
        ContestProblem link = contestProblemRepository.findByProblemId(problem.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + problem.getId()));
        Contest contest = link.getContest();
        if (ContestStatus.of(contest.getStartTime(), contest.getEndTime(), Instant.now())
                != ContestStatus.RUNNING) {
            throw new ResourceNotFoundException("Problem not found: " + problem.getId());
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User does not exist"));
        boolean admin = user.getRoles().stream()
                .anyMatch(role -> AppRole.ROLE_ADMIN.name().equals(role.getName()));
        if (admin) {
            return;
        }
        if (contest.isPrivate()) {
            throw new ResourceNotFoundException("Problem not found: " + problem.getId());
        }
        if (!contestParticipantRepository.existsByContestIdAndUserId(contest.getId(), userId)) {
            throw new ContestRegistrationRequiredException("Register for this contest to submit");
        }
    }

    private SubmissionJobMessage buildJobMessage(Submission submission, Problem problem, Language language) {
        List<Long> testCaseIds = testCaseRepository.findByProblemIdOrderByDisplayOrderAsc(problem.getId())
                .stream()
                .map(TestCase::getId)
                .toList();
        return new SubmissionJobMessage(
                submission.getId(),
                problem.getId(),
                language.getId().intValue(),
                effectiveTimeLimitMs(problem, language),
                problem.getMemoryLimitKb(),
                testCaseIds);
    }

    /** PRD §26: run limit = problem limit × language multiplier. */
    private static int effectiveTimeLimitMs(Problem problem, Language language) {
        return BigDecimal.valueOf(problem.getTimeLimitMs())
                .multiply(language.getTimeLimitMultiplier())
                .intValue();
    }
}
