package com.onlinejudge.backend.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.AdminProblemDetailResponse;
import com.onlinejudge.backend.api.dto.AdminProblemSummaryResponse;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.ProblemSpecifications;
import com.onlinejudge.backend.repository.ProblemTestCaseCount;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.common.entity.Problem;

/**
 * Authorization is enforced here as well as at the controller (AGENTS.md §10 defense in
 * depth). The list is intentionally unfiltered by publication so admins can see drafts.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminProblemQueryServiceImpl implements AdminProblemQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    public AdminProblemQueryServiceImpl(ProblemRepository problemRepository, TestCaseRepository testCaseRepository) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Override
    public PagedResponse<AdminProblemSummaryResponse> list(Boolean published, String search, int page, int size) {
        Specification<Problem> spec = (root, query, cb) -> cb.conjunction();
        if (published != null) {
            spec = spec.and(ProblemSpecifications.hasPublished(published));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(ProblemSpecifications.titleOrSlugContains(search));
        }

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        Page<Problem> problems = problemRepository.findAll(spec, pageRequest);
        Map<Long, Long> counts = countsFor(problems.getContent());
        return PagedResponse.of(problems,
                problem -> AdminProblemSummaryResponse.from(problem,
                        counts.getOrDefault(problem.getId(), 0L).intValue()));
    }

    @Override
    public AdminProblemDetailResponse get(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + id));
        return AdminProblemDetailResponse.from(problem, (int) testCaseRepository.countByProblemId(id));
    }

    private Map<Long, Long> countsFor(List<Problem> problems) {
        if (problems.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = problems.stream().map(Problem::getId).toList();
        return testCaseRepository.countByProblemIds(ids).stream()
                .collect(Collectors.toMap(ProblemTestCaseCount::getProblemId, ProblemTestCaseCount::getTotal,
                        (left, right) -> left));
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
