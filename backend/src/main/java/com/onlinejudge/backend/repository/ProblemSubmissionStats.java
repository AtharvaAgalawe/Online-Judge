package com.onlinejudge.backend.repository;

/** Projection for grouped per-problem submission statistics (used by the browse list). */
public interface ProblemSubmissionStats {

    Long getProblemId();

    long getTotal();

    long getAccepted();
}
