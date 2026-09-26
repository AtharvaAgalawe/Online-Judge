package com.onlinejudge.backend.api.dto;

/** Per-problem statistics (PRD §7.6). */
public record ProblemStatsResponse(long totalSubmissions, long acceptedCount, double acceptanceRate) {

    public static ProblemStatsResponse of(long totalSubmissions, long acceptedCount) {
        return new ProblemStatsResponse(totalSubmissions, acceptedCount,
                percentage(acceptedCount, totalSubmissions));
    }

    /** Percentage with one decimal; 0 when there are no submissions yet. */
    public static double percentage(long accepted, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round(accepted * 1000.0 / total) / 10.0;
    }
}
