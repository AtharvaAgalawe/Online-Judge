package com.onlinejudge.backend.service;

import java.util.List;

import com.onlinejudge.backend.api.dto.CreateProblemRequest;
import com.onlinejudge.backend.api.dto.CreateTestCaseRequest;
import com.onlinejudge.backend.api.dto.ProblemCreatedResponse;
import com.onlinejudge.backend.api.dto.ProblemDetailResponse;
import com.onlinejudge.backend.api.dto.TestCaseResponse;
import com.onlinejudge.backend.api.dto.UpdateProblemRequest;
import com.onlinejudge.backend.api.dto.UpdateTestCaseRequest;

/** Admin write side of problem/test-case authoring (PRD §7.7). */
public interface ProblemAdminService {

    ProblemCreatedResponse create(CreateProblemRequest request, String adminUsername);

    ProblemDetailResponse update(Long id, UpdateProblemRequest request);

    /** Soft delete: the problem stops being visible to solvers but history survives (PRD §15). */
    void unpublish(Long id);

    List<TestCaseResponse> listTestCases(Long problemId);

    TestCaseResponse addTestCase(Long problemId, CreateTestCaseRequest request);

    TestCaseResponse updateTestCase(Long problemId, Long testCaseId, UpdateTestCaseRequest request);

    void deleteTestCase(Long problemId, Long testCaseId);
}
