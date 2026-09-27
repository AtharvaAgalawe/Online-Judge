package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.SubmissionDetailResponse;
import com.onlinejudge.backend.api.dto.SubmissionStatusResponse;
import com.onlinejudge.backend.api.dto.SubmissionSummaryResponse;

/**
 * Read side of submissions. Ownership is enforced here, not only at the controller
 * (AGENTS.md §10): a non-admin can only ever read their own submissions.
 */
public interface SubmissionQueryService {

    SubmissionStatusResponse getStatus(Long submissionId);

    SubmissionDetailResponse getDetail(Long submissionId);

    PagedResponse<SubmissionSummaryResponse> history(Long problemId, Long userId, int page, int size);
}
