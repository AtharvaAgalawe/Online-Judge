package com.onlinejudge.backend.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateTestCaseRequest(
        @NotNull String input,
        @NotNull String expectedOutput,
        Boolean isSample,
        @Min(0) Integer order,
        @Min(1) Integer points) {
}
