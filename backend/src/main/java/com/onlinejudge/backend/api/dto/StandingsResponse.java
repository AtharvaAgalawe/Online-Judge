package com.onlinejudge.backend.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Contest standings (PRD §7.8). While {@code frozen} is true the rows are a snapshot cut off at
 * {@code frozenAt} (contest end minus the freeze window); after the contest ends the full
 * standings are served with {@code frozen} false and {@code frozenAt} null.
 */
public record StandingsResponse(boolean frozen, Instant frozenAt, List<StandingsRow> rows) {
}
