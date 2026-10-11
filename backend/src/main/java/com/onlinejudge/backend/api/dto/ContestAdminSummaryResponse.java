package com.onlinejudge.backend.api.dto;

import java.time.Instant;

import com.onlinejudge.common.enums.ContestStatus;

public record ContestAdminSummaryResponse(Long id, String name, Instant startTime, Instant endTime,
        ContestStatus status, boolean isPrivate, long participantCount, long problemCount,
        Instant problemsPublishedAt) {
}
