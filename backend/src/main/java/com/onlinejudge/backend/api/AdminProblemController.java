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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.CreateProblemRequest;
import com.onlinejudge.backend.api.dto.CreateTestCaseRequest;
import com.onlinejudge.backend.api.dto.ProblemCreatedResponse;
import com.onlinejudge.backend.api.dto.ProblemDetailResponse;
import com.onlinejudge.backend.api.dto.TestCaseResponse;
import com.onlinejudge.backend.api.dto.UpdateProblemRequest;
import com.onlinejudge.backend.api.dto.UpdateTestCaseRequest;
import com.onlinejudge.backend.service.ProblemAdminService;

import jakarta.validation.Valid;

/** Admin problem/test-case authoring (PRD §7.7, §15). */
@RestController
@RequestMapping("/api/v1/admin/problems")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProblemController {

    private final ProblemAdminService problemAdminService;

    public AdminProblemController(ProblemAdminService problemAdminService) {
        this.problemAdminService = problemAdminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemCreatedResponse create(@Valid @RequestBody CreateProblemRequest request,
                                         Authentication authentication) {
        return problemAdminService.create(request, authentication.getName());
    }

    @PutMapping("/{id}")
    public ProblemDetailResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProblemRequest request) {
        return problemAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unpublish(@PathVariable Long id) {
        problemAdminService.unpublish(id);
    }

    @GetMapping("/{id}/test-cases")
    public List<TestCaseResponse> listTestCases(@PathVariable Long id) {
        return problemAdminService.listTestCases(id);
    }

    @PostMapping("/{id}/test-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public TestCaseResponse addTestCase(@PathVariable Long id, @Valid @RequestBody CreateTestCaseRequest request) {
        return problemAdminService.addTestCase(id, request);
    }

    @PutMapping("/{id}/test-cases/{testCaseId}")
    public TestCaseResponse updateTestCase(@PathVariable Long id, @PathVariable Long testCaseId,
                                           @Valid @RequestBody UpdateTestCaseRequest request) {
        return problemAdminService.updateTestCase(id, testCaseId, request);
    }

    @DeleteMapping("/{id}/test-cases/{testCaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTestCase(@PathVariable Long id, @PathVariable Long testCaseId) {
        problemAdminService.deleteTestCase(id, testCaseId);
    }
}
