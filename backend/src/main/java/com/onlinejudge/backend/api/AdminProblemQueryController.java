package com.onlinejudge.backend.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.AdminProblemDetailResponse;
import com.onlinejudge.backend.api.dto.AdminProblemSummaryResponse;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.service.AdminProblemQueryService;

/** Admin problem read endpoints (PRD §7.7, §15); includes unpublished problems. */
@RestController
@RequestMapping("/api/v1/admin/problems")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProblemQueryController {

    private final AdminProblemQueryService adminProblemQueryService;

    public AdminProblemQueryController(AdminProblemQueryService adminProblemQueryService) {
        this.adminProblemQueryService = adminProblemQueryService;
    }

    @GetMapping
    public PagedResponse<AdminProblemSummaryResponse> list(
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminProblemQueryService.list(published, search, page, size);
    }

    @GetMapping("/{id}")
    public AdminProblemDetailResponse get(@PathVariable Long id) {
        return adminProblemQueryService.get(id);
    }
}
