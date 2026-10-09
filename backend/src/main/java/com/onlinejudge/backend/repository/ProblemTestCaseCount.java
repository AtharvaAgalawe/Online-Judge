package com.onlinejudge.backend.repository;

/** Projection for a per-problem test-case count in one grouped query. */
public interface ProblemTestCaseCount {

    Long getProblemId();

    long getTotal();
}
