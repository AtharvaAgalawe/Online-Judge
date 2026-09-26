package com.onlinejudge.backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.CreateProblemRequest;
import com.onlinejudge.backend.api.dto.CreateTestCaseRequest;
import com.onlinejudge.backend.api.dto.ProblemCreatedResponse;
import com.onlinejudge.backend.api.dto.ProblemDetailResponse;
import com.onlinejudge.backend.api.dto.SampleTestCaseResponse;
import com.onlinejudge.backend.api.dto.TestCaseResponse;
import com.onlinejudge.backend.api.dto.UpdateProblemRequest;
import com.onlinejudge.backend.api.dto.UpdateTestCaseRequest;
import com.onlinejudge.backend.exception.BusinessRuleException;
import com.onlinejudge.backend.exception.ResourceInUseException;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.TagRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Tag;
import com.onlinejudge.common.entity.TestCase;
import com.onlinejudge.common.entity.User;

/**
 * Authorization is enforced here as well as at the controller (AGENTS.md §10 defense in
 * depth): a controller reorganization must not silently drop the admin gate.
 */
@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class ProblemAdminServiceImpl implements ProblemAdminService {

    private static final int DEFAULT_TIME_LIMIT_MS = 2000;
    private static final int DEFAULT_MEMORY_LIMIT_KB = 262_144;

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;

    public ProblemAdminServiceImpl(ProblemRepository problemRepository, TestCaseRepository testCaseRepository,
                                   TagRepository tagRepository, UserRepository userRepository) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ProblemCreatedResponse create(CreateProblemRequest request, String adminUsername) {
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User does not exist"));
        String slug = SlugGenerator.unique(SlugGenerator.slugify(request.title()), problemRepository::existsBySlug);

        Problem problem = new Problem(slug, request.title(), request.statement(), request.difficulty(),
                request.timeLimitMs() == null ? DEFAULT_TIME_LIMIT_MS : request.timeLimitMs(),
                request.memoryLimitKb() == null ? DEFAULT_MEMORY_LIMIT_KB : request.memoryLimitKb(),
                admin);
        resolveTags(request.tags()).forEach(problem::addTag);

        problemRepository.saveAndFlush(problem);
        return new ProblemCreatedResponse(problem.getId(), problem.getSlug());
    }

    @Override
    public ProblemDetailResponse update(Long id, UpdateProblemRequest request) {
        Problem problem = problemById(id);

        if (request.title() != null) {
            problem.setTitle(request.title());
        }
        if (request.statement() != null) {
            problem.setStatement(request.statement());
        }
        if (request.difficulty() != null) {
            problem.setDifficulty(request.difficulty());
        }
        if (request.timeLimitMs() != null) {
            problem.setTimeLimitMs(request.timeLimitMs());
        }
        if (request.memoryLimitKb() != null) {
            problem.setMemoryLimitKb(request.memoryLimitKb());
        }
        if (request.tags() != null) {
            problem.clearTags();
            resolveTags(request.tags()).forEach(problem::addTag);
        }
        if (request.published() != null) {
            if (request.published() && testCaseRepository.countByProblemId(id) == 0) {
                throw new BusinessRuleException("Cannot publish a problem that has no test cases");
            }
            problem.setPublished(request.published());
        }

        problemRepository.save(problem);
        List<SampleTestCaseResponse> samples = testCaseRepository
                .findByProblemIdAndSampleTrueOrderByDisplayOrderAsc(id).stream()
                .map(SampleTestCaseResponse::from)
                .toList();
        return ProblemDetailResponse.from(problem, samples);
    }

    @Override
    public void unpublish(Long id) {
        Problem problem = problemById(id);
        problem.setPublished(false);
        problemRepository.save(problem);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestCaseResponse> listTestCases(Long problemId) {
        problemById(problemId);
        return testCaseRepository.findByProblemIdOrderByDisplayOrderAsc(problemId).stream()
                .map(TestCaseResponse::from)
                .toList();
    }

    @Override
    public TestCaseResponse addTestCase(Long problemId, CreateTestCaseRequest request) {
        Problem problem = problemById(problemId);
        TestCase testCase = new TestCase(request.input(), request.expectedOutput(),
                Boolean.TRUE.equals(request.isSample()),
                request.order() == null ? 0 : request.order(),
                request.points() == null ? 1 : request.points());
        problem.addTestCase(testCase);
        testCaseRepository.saveAndFlush(testCase);
        return TestCaseResponse.from(testCase);
    }

    @Override
    public TestCaseResponse updateTestCase(Long problemId, Long testCaseId, UpdateTestCaseRequest request) {
        TestCase testCase = testCaseOfProblem(problemId, testCaseId);
        if (request.input() != null) {
            testCase.setInput(request.input());
        }
        if (request.expectedOutput() != null) {
            testCase.setExpectedOutput(request.expectedOutput());
        }
        if (request.isSample() != null) {
            testCase.setSample(request.isSample());
        }
        if (request.order() != null) {
            testCase.setDisplayOrder(request.order());
        }
        if (request.points() != null) {
            testCase.setPoints(request.points());
        }
        testCaseRepository.save(testCase);
        return TestCaseResponse.from(testCase);
    }

    @Override
    public void deleteTestCase(Long problemId, Long testCaseId) {
        TestCase testCase = testCaseOfProblem(problemId, testCaseId);
        try {
            testCaseRepository.delete(testCase);
            // Force the DELETE now so a judged-history FK violation surfaces here, not at commit.
            testCaseRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Judged history references this test case; it must never be silently rewritten (PRD §7.7).
            throw new ResourceInUseException("Test case is referenced by judged submissions and cannot be deleted");
        }
    }

    private Problem problemById(Long id) {
        return problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + id));
    }

    private TestCase testCaseOfProblem(Long problemId, Long testCaseId) {
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test case not found: " + testCaseId));
        if (!testCase.getProblem().getId().equals(problemId)) {
            throw new ResourceNotFoundException("Test case not found: " + testCaseId);
        }
        return testCase;
    }

    /** Find-or-create, case-insensitive, preserving the first spelling seen. */
    private Set<Tag> resolveTags(List<String> names) {
        Map<String, Tag> resolved = new LinkedHashMap<>();
        if (names == null) {
            return Set.of();
        }
        for (String rawName : names) {
            String name = rawName.trim();
            if (name.isEmpty()) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            resolved.computeIfAbsent(key, ignored -> tagRepository.findByNameIgnoreCase(name)
                    .orElseGet(() -> tagRepository.save(new Tag(name))));
        }
        return Set.copyOf(resolved.values());
    }
}
