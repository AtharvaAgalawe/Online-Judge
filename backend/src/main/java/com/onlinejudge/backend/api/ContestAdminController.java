package com.onlinejudge.backend.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.AttachContestProblemRequest;
import com.onlinejudge.backend.api.dto.AttachableProblemResponse;
import com.onlinejudge.backend.api.dto.ContestAdminSummaryResponse;
import com.onlinejudge.backend.api.dto.ContestCreatedResponse;
import com.onlinejudge.backend.api.dto.ContestProblemCreatedResponse;
import com.onlinejudge.backend.api.dto.CreateContestProblemRequest;
import com.onlinejudge.backend.api.dto.CreateContestRequest;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.UpdateContestProblemRequest;
import com.onlinejudge.backend.api.dto.UpdateContestRequest;
import com.onlinejudge.backend.service.ContestAdminService;
import com.onlinejudge.common.enums.ContestStatus;

import jakarta.validation.Valid;

/** Admin contest management (PRD §7.8): CRUD, problem attach/detach, inline problem creation. */
@RestController
@RequestMapping("/api/v1/admin/contests")
@PreAuthorize("hasRole('ADMIN')")
public class ContestAdminController {

    private final ContestAdminService contestAdminService;

    public ContestAdminController(ContestAdminService contestAdminService) {
        this.contestAdminService = contestAdminService;
    }

    @GetMapping
    public PagedResponse<ContestAdminSummaryResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return contestAdminService.list(search, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContestCreatedResponse create(@Valid @RequestBody CreateContestRequest request,
                                         Authentication authentication) {
        return contestAdminService.create(request, authentication.getName());
    }

    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @Valid @RequestBody UpdateContestRequest request) {
        contestAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        contestAdminService.delete(id);
    }

    @PostMapping("/{id}/problems")
    @ResponseStatus(HttpStatus.CREATED)
    public void attachProblem(@PathVariable Long id, @Valid @RequestBody AttachContestProblemRequest request) {
        contestAdminService.attachProblem(id, request);
    }

    @PostMapping("/{id}/problems/new")
    @ResponseStatus(HttpStatus.CREATED)
    public ContestProblemCreatedResponse createProblem(@PathVariable Long id,
                                                       @Valid @RequestBody CreateContestProblemRequest request,
                                                       Authentication authentication) {
        return contestAdminService.createProblem(id, request, authentication.getName());
    }

    @PutMapping("/{id}/problems/{problemId}")
    public void updateProblem(@PathVariable Long id, @PathVariable Long problemId,
                              @Valid @RequestBody UpdateContestProblemRequest request) {
        contestAdminService.updateProblem(id, problemId, request);
    }

    @DeleteMapping("/{id}/problems/{problemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detachProblem(@PathVariable Long id, @PathVariable Long problemId) {
        contestAdminService.detachProblem(id, problemId);
    }

    @GetMapping("/attachable-problems")
    public List<AttachableProblemResponse> listAttachableProblems(
            @RequestParam(required = false) String search) {
        return contestAdminService.listAttachableProblems(search);
    }
}
