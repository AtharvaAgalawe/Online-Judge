package com.onlinejudge.backend.api.dto;

import java.time.Instant;
import java.util.List;

import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Tag;
import com.onlinejudge.common.enums.Difficulty;

/** Admin problem detail for editing (PRD §7.7): includes {@code published} and authorship. */
public record AdminProblemDetailResponse(
        Long id,
        String slug,
        String title,
        String statement,
        Difficulty difficulty,
        int timeLimitMs,
        int memoryLimitKb,
        boolean published,
        String createdBy,
        int testCaseCount,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminProblemDetailResponse from(Problem problem, int testCaseCount) {
        return new AdminProblemDetailResponse(problem.getId(), problem.getSlug(), problem.getTitle(),
                problem.getStatement(), problem.getDifficulty(), problem.getTimeLimitMs(),
                problem.getMemoryLimitKb(), problem.isPublished(),
                problem.getCreatedBy() == null ? null : problem.getCreatedBy().getUsername(),
                testCaseCount,
                problem.getTags().stream().map(Tag::getName).sorted().toList(),
                problem.getCreatedAt(), problem.getUpdatedAt());
    }
}
