package com.onlinejudge.backend.api.dto;

import com.onlinejudge.common.enums.Difficulty;

/** Contest problem row; {@code solvedByMe} is the caller's pre-contest solved mark. */
public record ContestProblemSummaryResponse(Long problemId, String slug, String title,
        Difficulty difficulty, int displayOrder, int points, boolean solvedByMe) {
}
