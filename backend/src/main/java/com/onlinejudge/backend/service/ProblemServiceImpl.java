package com.onlinejudge.backend.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.ProblemDetailResponse;
import com.onlinejudge.backend.api.dto.ProblemStatsResponse;
import com.onlinejudge.backend.api.dto.ProblemSummaryResponse;
import com.onlinejudge.backend.api.dto.SampleTestCaseResponse;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.ProblemSpecifications;
import com.onlinejudge.backend.repository.ProblemSubmissionStats;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.Verdict;

@Service
@Transactional(readOnly = true)
public class ProblemServiceImpl implements ProblemService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionRepository submissionRepository;

    public ProblemServiceImpl(ProblemRepository problemRepository, TestCaseRepository testCaseRepository,
                              SubmissionRepository submissionRepository) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.submissionRepository = submissionRepository;
    }

    @Override
    public PagedResponse<ProblemSummaryResponse> browse(int page, int size, Difficulty difficulty, String tag,
                                                        String search) {
        Specification<Problem> spec = ProblemSpecifications.publishedOnly();
        if (difficulty != null) {
            spec = spec.and(ProblemSpecifications.hasDifficulty(difficulty));
        }
        if (tag != null && !tag.isBlank()) {
            spec = spec.and(ProblemSpecifications.hasTag(tag));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(ProblemSpecifications.titleOrSlugContains(search));
        }

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Problem> problems = problemRepository.findAll(spec, pageRequest);

        Map<Long, ProblemSubmissionStats> stats = statsFor(problems.getContent());
        return PagedResponse.of(problems, problem -> {
            ProblemSubmissionStats stat = stats.get(problem.getId());
            double rate = stat == null ? 0.0 : ProblemStatsResponse.percentage(stat.getAccepted(), stat.getTotal());
            return ProblemSummaryResponse.from(problem, rate);
        });
    }

    @Override
    public ProblemDetailResponse getBySlug(String slug) {
        Problem problem = publishedProblem(slug);
        List<SampleTestCaseResponse> samples = testCaseRepository
                .findByProblemIdAndSampleTrueOrderByDisplayOrderAsc(problem.getId()).stream()
                .map(SampleTestCaseResponse::from)
                .toList();
        return ProblemDetailResponse.from(problem, samples);
    }

    @Override
    public ProblemStatsResponse stats(String slug) {
        Problem problem = publishedProblem(slug);
        return ProblemStatsResponse.of(
                submissionRepository.countByProblemId(problem.getId()),
                submissionRepository.countByProblemIdAndVerdict(problem.getId(), Verdict.ACCEPTED));
    }

    /** Unpublished problems are fully invisible to solvers, including by direct slug (PRD §7.7). */
    private Problem publishedProblem(String slug) {
        return problemRepository.findBySlug(slug)
                .filter(Problem::isPublished)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + slug));
    }

    private Map<Long, ProblemSubmissionStats> statsFor(List<Problem> problems) {
        if (problems.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = problems.stream().map(Problem::getId).toList();
        return submissionRepository.aggregateStatsByProblemIds(ids, Verdict.ACCEPTED).stream()
                .collect(Collectors.toMap(ProblemSubmissionStats::getProblemId, Function.identity()));
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
