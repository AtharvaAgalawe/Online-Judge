package com.onlinejudge.backend.api.dto;

import java.util.List;

import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Tag;
import com.onlinejudge.common.enums.Difficulty;

/** Browse-list item (PRD §7.1). {@code acceptanceRate} is a percentage (0–100, one decimal). */
public record ProblemSummaryResponse(
        Long id,
        String slug,
        String title,
        Difficulty difficulty,
        List<String> tags,
        double acceptanceRate) {

    public static ProblemSummaryResponse from(Problem problem, double acceptanceRate) {
        return new ProblemSummaryResponse(problem.getId(), problem.getSlug(), problem.getTitle(),
                problem.getDifficulty(), problem.getTags().stream().map(Tag::getName).sorted().toList(),
                acceptanceRate);
    }
}
