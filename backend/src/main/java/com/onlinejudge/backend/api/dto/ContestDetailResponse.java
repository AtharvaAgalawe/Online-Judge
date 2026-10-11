package com.onlinejudge.backend.api.dto;

import java.time.Instant;
import java.util.List;

import com.onlinejudge.common.enums.ContestStatus;

/**
 * Public contest detail. {@code problems} is null while hidden (upcoming, non-admin caller);
 * see {@code problemsRevealed}.
 */
public record ContestDetailResponse(Long id, String name, String description, Instant startTime,
        Instant endTime, ContestStatus status, int freezeMinutes, long participantCount,
        boolean registered, boolean registrationOpen, boolean problemsRevealed,
        List<ContestProblemSummaryResponse> problems) {
}
