package com.onlinejudge.backend.api.dto;

import java.time.Instant;
import java.util.List;

import com.onlinejudge.common.enums.ContestStatus;

public record ContestAdminDetailResponse(Long id, String name, String description, Instant startTime,
        Instant endTime, boolean isPrivate, int freezeMinutes, ContestStatus status,
        Instant problemsPublishedAt, List<ContestProblemSummaryResponse> problems) {
}
