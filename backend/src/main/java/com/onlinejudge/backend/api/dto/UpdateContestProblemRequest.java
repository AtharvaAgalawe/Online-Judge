package com.onlinejudge.backend.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateContestProblemRequest(@Min(1) @Max(10000) Integer points, @Min(0) Integer displayOrder) {
}
