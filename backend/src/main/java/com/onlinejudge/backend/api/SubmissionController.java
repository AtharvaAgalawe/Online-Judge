package com.onlinejudge.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.CreateSubmissionRequest;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.SubmissionAcceptedResponse;
import com.onlinejudge.backend.api.dto.SubmissionDetailResponse;
import com.onlinejudge.backend.api.dto.SubmissionStatusResponse;
import com.onlinejudge.backend.api.dto.SubmissionSummaryResponse;
import com.onlinejudge.backend.service.SubmissionQueryService;
import com.onlinejudge.backend.service.SubmissionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;
    private final SubmissionQueryService submissionQueryService;

    public SubmissionController(SubmissionService submissionService,
                                SubmissionQueryService submissionQueryService) {
        this.submissionService = submissionService;
        this.submissionQueryService = submissionQueryService;
    }

    /**
     * Returns 202 immediately with the submission id; judging happens asynchronously
     * (PRD §12). A repeated request with the same {@code Idempotency-Key} returns the
     * original submission rather than creating a second one.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("isAuthenticated()")
    public SubmissionAcceptedResponse create(@Valid @RequestBody CreateSubmissionRequest request,
                                             @RequestHeader(value = "Idempotency-Key", required = false)
                                             String idempotencyKey,
                                             Authentication authentication) {
        return submissionService.create(authentication.getName(), request, idempotencyKey);
    }

    /** Full detail for the owner or an admin; hidden test content is never included (PRD §7.4). */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SubmissionDetailResponse detail(@PathVariable Long id) {
        return submissionQueryService.getDetail(id);
    }

    /** Lightweight polling endpoint (PRD §15). */
    @GetMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public SubmissionStatusResponse status(@PathVariable Long id) {
        return submissionQueryService.getStatus(id);
    }

    /** The caller's own history; admins may filter by any {@code userId} (PRD §15). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PagedResponse<SubmissionSummaryResponse> history(
            @RequestParam(required = false) Long problemId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return submissionQueryService.history(problemId, userId, page, size);
    }
}
