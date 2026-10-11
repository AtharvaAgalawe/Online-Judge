package com.onlinejudge.backend.api.dto;

import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.enums.Difficulty;

public record AttachableProblemResponse(Long id, String slug, String title, Difficulty difficulty) {

    public static AttachableProblemResponse from(Problem problem) {
        return new AttachableProblemResponse(problem.getId(), problem.getSlug(), problem.getTitle(),
                problem.getDifficulty());
    }
}
