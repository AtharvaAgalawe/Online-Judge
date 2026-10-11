package com.onlinejudge.backend.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.ContestDetailResponse;
import com.onlinejudge.backend.api.dto.ContestProblemDetailResponse;
import com.onlinejudge.backend.api.dto.ContestSummaryResponse;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.RegistrationResponse;
import com.onlinejudge.backend.service.ContestProblemService;
import com.onlinejudge.backend.service.ContestPublicService;
import com.onlinejudge.common.enums.ContestStatus;

/** Public contest browsing and registration (PRD §7.8); writes require authentication. */
@RestController
@RequestMapping("/api/v1/contests")
public class ContestController {

    private final ContestPublicService contestPublicService;
    private final ContestProblemService contestProblemService;

    public ContestController(ContestPublicService contestPublicService,
                             ContestProblemService contestProblemService) {
        this.contestPublicService = contestPublicService;
        this.contestProblemService = contestProblemService;
    }

    @GetMapping
    public PagedResponse<ContestSummaryResponse> browse(
            @RequestParam(required = false) ContestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return contestPublicService.browse(status, page, size);
    }

    @GetMapping("/{id}")
    public ContestDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        return contestPublicService.detail(id, principalOrNull(authentication));
    }

    @GetMapping("/{id}/problems/{slug}")
    public ContestProblemDetailResponse problem(@PathVariable Long id, @PathVariable String slug,
                                                Authentication authentication) {
        return contestProblemService.detail(id, slug, principalOrNull(authentication));
    }

    @PostMapping("/{id}/register")
    @PreAuthorize("isAuthenticated()")
    public RegistrationResponse register(@PathVariable Long id, Authentication authentication) {
        return contestPublicService.register(id, authentication.getName());
    }

    @DeleteMapping("/{id}/register")
    @PreAuthorize("isAuthenticated()")
    public void unregister(@PathVariable Long id, Authentication authentication) {
        contestPublicService.unregister(id, authentication.getName());
    }

    /** Resolves the caller for optional-auth GETs; anonymous/absent auth → null. */
    static String principalOrNull(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}
