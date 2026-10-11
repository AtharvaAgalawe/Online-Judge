package com.onlinejudge.backend.api.dto;

import com.onlinejudge.common.enums.Difficulty;

/** Contest problem row; {@code solvedByMe} is the caller's own first-AC on the problem
 *  (any time, not contest-window restricted). */
public record ContestProblemSummaryResponse(Long problemId, String slug, String title,
        Difficulty difficulty, int displayOrder, int points, boolean solvedByMe) {
}
