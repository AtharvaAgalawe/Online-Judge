package com.onlinejudge.backend.api.dto;

import com.onlinejudge.common.entity.TestCase;

/** Public sample test case — content visible to solvers by design (PRD §7.1). */
public record SampleTestCaseResponse(String input, String expectedOutput, int order) {

    public static SampleTestCaseResponse from(TestCase testCase) {
        return new SampleTestCaseResponse(testCase.getInput(), testCase.getExpectedOutput(),
                testCase.getDisplayOrder());
    }
}
