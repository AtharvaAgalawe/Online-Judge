package com.onlinejudge.backend.api.dto;

import java.time.Instant;

import com.onlinejudge.common.enums.ContestStatus;

/** Public contest list row (PRD §7.8). */
public record ContestSummaryResponse(Long id, String name, Instant startTime, Instant endTime,
        ContestStatus status, long participantCount, long problemCount) {
}
