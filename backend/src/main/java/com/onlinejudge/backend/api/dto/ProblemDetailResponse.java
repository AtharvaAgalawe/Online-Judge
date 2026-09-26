package com.onlinejudge.backend.api.dto;

import java.util.List;

import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Tag;
import com.onlinejudge.common.enums.Difficulty;

/** Public problem detail: statement, limits, and sample test cases only — never hidden tests. */
public record ProblemDetailResponse(
        Long id,
        String slug,
        String title,
        String statement,
        Difficulty difficulty,
        int timeLimitMs,
        int memoryLimitKb,
        List<String> tags,
        List<SampleTestCaseResponse> sampleTestCases) {

    public static ProblemDetailResponse from(Problem problem, List<SampleTestCaseResponse> samples) {
        return new ProblemDetailResponse(problem.getId(), problem.getSlug(), problem.getTitle(),
                problem.getStatement(), problem.getDifficulty(), problem.getTimeLimitMs(),
                problem.getMemoryLimitKb(),
                problem.getTags().stream().map(Tag::getName).sorted().toList(), samples);
    }
}
