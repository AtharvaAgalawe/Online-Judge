package com.onlinejudge.backend.api.dto;

import java.util.List;

import com.onlinejudge.common.enums.Difficulty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update (PRD §15): every field is optional; {@code null} means "leave unchanged".
 * {@code published} is the publish/unpublish toggle — publishing requires at least one
 * test case (PRD §7.7).
 */
public record UpdateProblemRequest(
        @Size(max = 200) String title,

        String statement,

        Difficulty difficulty,

        @Min(100) @Max(60_000) Integer timeLimitMs,

        @Min(1_024) @Max(2_097_152) Integer memoryLimitKb,

        @Size(max = 10) List<@NotBlank @Size(max = 50) @Pattern(regexp = "^[\\w+#. -]+$") String> tags,

        Boolean published) {
}
