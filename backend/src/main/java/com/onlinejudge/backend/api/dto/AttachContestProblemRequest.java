package com.onlinejudge.backend.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AttachContestProblemRequest(@NotNull Long problemId,

        @Min(1) @Max(10000) Integer points) {
}
