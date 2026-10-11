package com.onlinejudge.backend.api.dto;

import com.onlinejudge.common.enums.Difficulty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateContestProblemRequest(
        @NotBlank @Size(max = 200) String title,

        @NotBlank String statement,

        @NotNull Difficulty difficulty,

        @Min(100) @Max(60_000) Integer timeLimitMs,

        @Min(1_024) @Max(2_097_152) Integer memoryLimitKb,

        @Min(1) @Max(10000) Integer points) {
}
