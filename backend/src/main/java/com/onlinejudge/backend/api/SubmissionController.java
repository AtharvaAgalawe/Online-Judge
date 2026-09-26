package com.onlinejudge.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.CreateSubmissionRequest;
import com.onlinejudge.backend.api.dto.SubmissionAcceptedResponse;
import com.onlinejudge.backend.service.SubmissionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
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
}
