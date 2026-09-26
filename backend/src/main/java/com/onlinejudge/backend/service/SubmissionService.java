package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.CreateSubmissionRequest;
import com.onlinejudge.backend.api.dto.SubmissionAcceptedResponse;

public interface SubmissionService {

    /**
     * @param idempotencyKey optional client-supplied key; replays of the same key return
     *                       the original submission instead of creating a second one
     */
    SubmissionAcceptedResponse create(String username, CreateSubmissionRequest request, String idempotencyKey);

    /**
     * Marks a submission QUEUED after its job message has been published. Runs in its own
     * transaction: it must not be part of (or rolled back with) any caller's unit of work.
     */
    void markQueued(Long submissionId);
}
