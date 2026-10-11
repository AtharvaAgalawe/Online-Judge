package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.StandingsResponse;

/**
 * Contest standings with points, penalty, freeze snapshot, and competition ranking (PRD §7.8).
 */
public interface StandingsService {

    /**
     * @param usernameOrNull caller's username for private-contest visibility, or null when
     *                       the request is anonymous
     */
    StandingsResponse standings(Long contestId, String usernameOrNull);
}
