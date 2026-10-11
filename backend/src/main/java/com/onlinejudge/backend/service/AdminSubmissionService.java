package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.SubmissionSummaryResponse;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/** Admin read side across all users' submissions (PRD §7.7, §15). */
public interface AdminSubmissionService {

    PagedResponse<SubmissionSummaryResponse> list(Long problemId, Long userId, SubmissionStatus status,
                                                  Verdict verdict, int page, int size);
}
