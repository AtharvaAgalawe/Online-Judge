package com.onlinejudge.backend.api.dto;

/**
 * Standings cell for one problem. {@code acceptedAtMinute} is null until solved;
 * {@code wrongAttempts} counts terminal non-accepted, non-SYSTEM_ERROR attempts before the
 * first AC (all such attempts while unsolved).
 */
public record StandingsProblemCell(Long problemId, boolean solved, int wrongAttempts,
        Integer acceptedAtMinute) {
}
