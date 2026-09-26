package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.ProblemDetailResponse;
import com.onlinejudge.backend.api.dto.ProblemStatsResponse;
import com.onlinejudge.backend.api.dto.ProblemSummaryResponse;
import com.onlinejudge.common.enums.Difficulty;

/** Public, solver-facing read side of the problem catalog (PRD §7.1, §7.6). */
public interface ProblemService {

    PagedResponse<ProblemSummaryResponse> browse(int page, int size, Difficulty difficulty, String tag,
                                                 String search);

    ProblemDetailResponse getBySlug(String slug);

    ProblemStatsResponse stats(String slug);
}
