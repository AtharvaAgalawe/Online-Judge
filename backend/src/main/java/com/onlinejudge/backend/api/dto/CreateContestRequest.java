package com.onlinejudge.backend.api.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateContestRequest(
        @NotBlank @Size(max = 200) String name,

        @Size(max = 5000) String description,

        @NotNull Instant startTime,

        @NotNull Instant endTime,

        boolean isPrivate,

        @Min(0) @Max(1440) Integer freezeMinutes) {
}
