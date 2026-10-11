package com.onlinejudge.backend.api.dto;

import java.util.List;

/** One participant's standings row: rank, totals, and per-problem cells (PRD §7.8). */
public record StandingsRow(int rank, String username, int totalPoints, int penalty,
        List<StandingsProblemCell> problems) {
}
