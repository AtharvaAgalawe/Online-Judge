package com.onlinejudge.backend.api.dto;

import java.util.List;

import com.onlinejudge.common.enums.ContestStatus;
import com.onlinejudge.common.enums.Difficulty;

/** Full problem statement for a contest problem page (PRD §7.8). */
public record ContestProblemDetailResponse(Long problemId, String slug, String title, String statement,
        Difficulty difficulty, int timeLimitMs, int memoryLimitKb, int points, Long contestId,
        String contestName, ContestStatus contestStatus, List<SampleTestCaseResponse> sampleTestCases) {
}
