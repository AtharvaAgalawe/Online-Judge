package com.onlinejudge.backend.api.dto;

import java.time.Instant;
import java.util.List;

import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Tag;
import com.onlinejudge.common.enums.Difficulty;

/** Admin list-row (PRD §7.7): unlike the public summary, includes unpublished problems. */
public record AdminProblemSummaryResponse(
        Long id,
        String slug,
        String title,
        Difficulty difficulty,
        boolean published,
        int testCaseCount,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminProblemSummaryResponse from(Problem problem, int testCaseCount) {
        return new AdminProblemSummaryResponse(problem.getId(), problem.getSlug(), problem.getTitle(),
                problem.getDifficulty(), problem.isPublished(), testCaseCount,
                problem.getTags().stream().map(Tag::getName).sorted().toList(),
                problem.getCreatedAt(), problem.getUpdatedAt());
    }
}
