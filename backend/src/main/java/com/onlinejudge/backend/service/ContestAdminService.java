package com.onlinejudge.backend.service;

import java.util.List;

import com.onlinejudge.backend.api.dto.AttachContestProblemRequest;
import com.onlinejudge.backend.api.dto.AttachableProblemResponse;
import com.onlinejudge.backend.api.dto.ContestAdminSummaryResponse;
import com.onlinejudge.backend.api.dto.ContestCreatedResponse;
import com.onlinejudge.backend.api.dto.ContestProblemCreatedResponse;
import com.onlinejudge.backend.api.dto.CreateContestProblemRequest;
import com.onlinejudge.backend.api.dto.CreateContestRequest;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.UpdateContestProblemRequest;
import com.onlinejudge.backend.api.dto.UpdateContestRequest;
import com.onlinejudge.common.enums.ContestStatus;

/** Admin side of contest management: CRUD, problem attach/detach, inline problem creation. */
public interface ContestAdminService {

    PagedResponse<ContestAdminSummaryResponse> list(String search, ContestStatus status, int page, int size);

    ContestCreatedResponse create(CreateContestRequest request, String adminUsername);

    void update(Long id, UpdateContestRequest request);

    void delete(Long id);

    void attachProblem(Long id, AttachContestProblemRequest request);

    ContestProblemCreatedResponse createProblem(Long id, CreateContestProblemRequest request, String adminUsername);

    void updateProblem(Long id, Long problemId, UpdateContestProblemRequest request);

    void detachProblem(Long id, Long problemId);

    List<AttachableProblemResponse> listAttachableProblems(String search);
}
