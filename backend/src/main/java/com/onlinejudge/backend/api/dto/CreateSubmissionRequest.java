package com.onlinejudge.backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Submission input (PRD §7.2). The optional Idempotency-Key travels in a header. */
public record CreateSubmissionRequest(
        @NotNull Long problemId,

        @NotNull Long languageId,

        @NotBlank @Size(max = 65_536, message = "must not exceed 65536 characters") String sourceCode) {
}
