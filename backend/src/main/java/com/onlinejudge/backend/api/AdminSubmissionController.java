package com.onlinejudge.backend.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.SubmissionSummaryResponse;
import com.onlinejudge.backend.service.AdminSubmissionService;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/** Admin submissions browser (PRD §7.7, §15): lists submissions across all users. */
@RestController
@RequestMapping("/api/v1/admin/submissions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubmissionController {

    private final AdminSubmissionService adminSubmissionService;

    public AdminSubmissionController(AdminSubmissionService adminSubmissionService) {
        this.adminSubmissionService = adminSubmissionService;
    }

    @GetMapping
    public PagedResponse<SubmissionSummaryResponse> list(
            @RequestParam(required = false) Long problemId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(required = false) Verdict verdict,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminSubmissionService.list(problemId, userId, status, verdict, page, size);
    }
}
