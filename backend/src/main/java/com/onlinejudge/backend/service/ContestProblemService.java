package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.ContestProblemDetailResponse;

/** Problem statement access inside a contest, gated by contest status and registration (PRD §7.8). */
public interface ContestProblemService {

    ContestProblemDetailResponse detail(Long contestId, String slug, String usernameOrNull);
}
