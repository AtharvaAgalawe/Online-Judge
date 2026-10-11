package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.ContestDetailResponse;
import com.onlinejudge.backend.api.dto.ContestSummaryResponse;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.RegistrationResponse;
import com.onlinejudge.common.enums.ContestStatus;

/** Public contest browsing and participant registration (PRD §7.8). */
public interface ContestPublicService {

    PagedResponse<ContestSummaryResponse> browse(ContestStatus status, int page, int size);

    ContestDetailResponse detail(Long id, String usernameOrNull);

    RegistrationResponse register(Long id, String username);

    void unregister(Long id, String username);
}
