package com.onlinejudge.backend.api.dto;

import com.onlinejudge.common.enums.SubmissionStatus;

/** 202 Accepted response (PRD §15): the id is available before any judging happens. */
public record SubmissionAcceptedResponse(Long submissionId, SubmissionStatus status) {
}
