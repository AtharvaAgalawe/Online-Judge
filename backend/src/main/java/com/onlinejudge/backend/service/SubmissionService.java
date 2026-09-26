package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.CreateSubmissionRequest;
import com.onlinejudge.backend.api.dto.SubmissionAcceptedResponse;

public interface SubmissionService {

    /**
     * @param idempotencyKey optional client-supplied key; replays of the same key return
     *                       the original submission instead of creating a second one
     */
    SubmissionAcceptedResponse create(String username, CreateSubmissionRequest request, String idempotencyKey);
}
