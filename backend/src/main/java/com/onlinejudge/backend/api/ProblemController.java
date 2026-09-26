package com.onlinejudge.backend.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.ProblemDetailResponse;
import com.onlinejudge.backend.api.dto.ProblemStatsResponse;
import com.onlinejudge.backend.api.dto.ProblemSummaryResponse;
import com.onlinejudge.backend.service.ProblemService;
import com.onlinejudge.common.enums.Difficulty;

/** Public problem browsing — deliberately unauthenticated (PRD §15). */
@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public PagedResponse<ProblemSummaryResponse> browse(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String search) {
        return problemService.browse(page, size, difficulty, tag, search);
    }

    @GetMapping("/{slug}")
    public ProblemDetailResponse detail(@PathVariable String slug) {
        return problemService.getBySlug(slug);
    }

    @GetMapping("/{slug}/stats")
    public ProblemStatsResponse stats(@PathVariable String slug) {
        return problemService.stats(slug);
    }
}
