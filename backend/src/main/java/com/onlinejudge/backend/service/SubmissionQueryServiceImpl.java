package com.onlinejudge.backend.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.SubmissionDetailResponse;
import com.onlinejudge.backend.api.dto.SubmissionStatusResponse;
import com.onlinejudge.backend.api.dto.SubmissionSummaryResponse;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.SubmissionResultRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.SubmissionResult;
import com.onlinejudge.common.entity.User;

@Service
@Transactional(readOnly = true)
public class SubmissionQueryServiceImpl implements SubmissionQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final SubmissionRepository submissionRepository;
    private final SubmissionResultRepository submissionResultRepository;
    private final UserRepository userRepository;

    public SubmissionQueryServiceImpl(SubmissionRepository submissionRepository,
                                      SubmissionResultRepository submissionResultRepository,
                                      UserRepository userRepository) {
        this.submissionRepository = submissionRepository;
        this.submissionResultRepository = submissionResultRepository;
        this.userRepository = userRepository;
    }

    @Override
    public SubmissionStatusResponse getStatus(Long submissionId) {
        Submission submission = ownedSubmission(submissionId);
        return new SubmissionStatusResponse(submission.getStatus(), submission.getVerdict());
    }

    @Override
    public SubmissionDetailResponse getDetail(Long submissionId) {
        Submission submission = ownedSubmission(submissionId);
        List<SubmissionDetailResponse.CaseResult> results = submissionResultRepository
                .findBySubmissionIdOrderByTestCaseDisplayOrderAsc(submissionId).stream()
                .map(SubmissionQueryServiceImpl::toCaseResult)
                .toList();
        SubmissionDetailResponse.FailedTestCase failed = submission.getFailedTestCase() == null
                ? null
                : new SubmissionDetailResponse.FailedTestCase(
                        submission.getFailedTestCase().getDisplayOrder() + 1,
                        submission.getFailedTestCase().isSample());

        return new SubmissionDetailResponse(
                submission.getId(),
                submission.getProblem().getId(),
                submission.getProblem().getSlug(),
                submission.getLanguage().getName(),
                submission.getStatus(),
                submission.getVerdict(),
                submission.getTimeUsedMs(),
                submission.getMemoryUsedKb(),
                submission.getErrorMessage(),
                submission.getSourceCode(),
                submission.getSubmittedAt(),
                submission.getJudgedAt(),
                failed,
                results);
    }

    @Override
    public PagedResponse<SubmissionSummaryResponse> history(Long problemId, Long userId, int page, int size) {
        Long effectiveUserId = resolveUserId(userId);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampSize(size));
        Page<Submission> submissions = problemId == null
                ? submissionRepository.findByUserIdOrderBySubmittedAtDesc(effectiveUserId, pageRequest)
                : submissionRepository.findByProblemIdAndUserIdOrderBySubmittedAtDesc(problemId, effectiveUserId,
                        pageRequest);
        return PagedResponse.of(submissions, SubmissionSummaryResponse::from);
    }

    /**
     * A user may only read their own submissions; admins may read anyone's (PRD §15).
     * The check lives in the service so a controller reorganization cannot drop it.
     */
    private Submission ownedSubmission(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));
        if (!isAdmin() && !submission.getUser().getUsername().equals(currentUsername())) {
            throw new AccessDeniedException("Submission belongs to another user");
        }
        return submission;
    }

    /** The {@code userId} filter is an admin-only capability (PRD §15). */
    private Long resolveUserId(Long requestedUserId) {
        if (requestedUserId == null) {
            return currentUser().getId();
        }
        if (!isAdmin()) {
            throw new AccessDeniedException("Only admins may query another user's submissions");
        }
        return requestedUserId;
    }

    private User currentUser() {
        return userRepository.findByUsername(currentUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User does not exist"));
    }

    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    private static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    private static SubmissionDetailResponse.CaseResult toCaseResult(SubmissionResult result) {
        return new SubmissionDetailResponse.CaseResult(
                result.getTestCase().getDisplayOrder() + 1,
                result.getTestCase().isSample(),
                result.getVerdict(),
                result.getTimeUsedMs(),
                result.getMemoryUsedKb());
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
