package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.AdminProblemDetailResponse;
import com.onlinejudge.backend.api.dto.AdminProblemSummaryResponse;
import com.onlinejudge.backend.api.dto.PagedResponse;

/** Admin read side of the problem catalog (PRD §7.7): includes unpublished problems. */
public interface AdminProblemQueryService {

    PagedResponse<AdminProblemSummaryResponse> list(Boolean published, String search, int page, int size);

    AdminProblemDetailResponse get(Long id);
}
