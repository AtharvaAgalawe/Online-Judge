package com.onlinejudge.backend.api.dto;

import com.onlinejudge.common.entity.TestCase;

/** Admin view of a test case — includes hidden content, so admin-only endpoints only. */
public record TestCaseResponse(Long id, String input, String expectedOutput, boolean isSample, int order, int points) {

    public static TestCaseResponse from(TestCase testCase) {
        return new TestCaseResponse(testCase.getId(), testCase.getInput(), testCase.getExpectedOutput(),
                testCase.isSample(), testCase.getDisplayOrder(), testCase.getPoints());
    }
}
