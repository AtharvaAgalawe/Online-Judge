package com.onlinejudge.backend.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.config.SubmissionProperties;
import com.onlinejudge.backend.exception.BusinessRuleException;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ExecutionJobRepository;
import com.onlinejudge.backend.repository.LanguageRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.TestCase;
import com.onlinejudge.common.entity.User;

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
    private final ApplicationEventPublisher eventPublisher;
    private final SubmissionProperties properties;

    public SubmissionCreator(SubmissionRepository submissionRepository, ExecutionJobRepository executionJobRepository,
                             ProblemRepository problemRepository, LanguageRepository languageRepository,
                             UserRepository userRepository, TestCaseRepository testCaseRepository,
                             ApplicationEventPublisher eventPublisher, SubmissionProperties properties) {
        this.submissionRepository = submissionRepository;
        this.executionJobRepository = executionJobRepository;
        this.problemRepository = problemRepository;
        this.languageRepository = languageRepository;
        this.userRepository = userRepository;
        this.testCaseRepository = testCaseRepository;
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

        // Unpublished problems are indistinguishable from missing ones to solvers (PRD §7.2).
        Problem problem = problemRepository.findById(problemId)
                .filter(Problem::isPublished)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + problemId));
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
