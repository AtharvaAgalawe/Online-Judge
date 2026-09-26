package com.onlinejudge.backend.api.dto;

import jakarta.validation.constraints.Min;

/** Partial update; {@code null} leaves the field unchanged. */
public record UpdateTestCaseRequest(
        String input,
        String expectedOutput,
        Boolean isSample,
        @Min(0) Integer order,
        @Min(1) Integer points) {
}
