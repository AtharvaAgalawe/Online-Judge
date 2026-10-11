package com.onlinejudge.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.SubmissionSummaryResponse;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.SubmissionSpecifications;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/** Authorization is enforced here as well as at the controller (AGENTS.md §10). */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubmissionServiceImpl implements AdminSubmissionService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final SubmissionRepository submissionRepository;

    public AdminSubmissionServiceImpl(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Override
    public PagedResponse<SubmissionSummaryResponse> list(Long problemId, Long userId, SubmissionStatus status,
                                                         Verdict verdict, int page, int size) {
        Specification<Submission> spec = (root, query, cb) -> cb.conjunction();
        if (problemId != null) {
            spec = spec.and(SubmissionSpecifications.hasProblem(problemId));
        }
        if (userId != null) {
            spec = spec.and(SubmissionSpecifications.hasUser(userId));
        }
        if (status != null) {
            spec = spec.and(SubmissionSpecifications.hasStatus(status));
        }
        if (verdict != null) {
            spec = spec.and(SubmissionSpecifications.hasVerdict(verdict));
        }

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "submittedAt", "id"));
        Page<Submission> submissions = submissionRepository.findAll(spec, pageRequest);
        return PagedResponse.of(submissions, SubmissionSummaryResponse::from);
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
