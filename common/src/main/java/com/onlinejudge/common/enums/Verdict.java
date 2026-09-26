package com.onlinejudge.common.enums;

/**
 * Final judgement for a submission or a single test case (PRD §1, §20). Set only when a
 * submission reaches a terminal state; never mutated afterwards (PRD §7.4).
 */
public enum Verdict {
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    COMPILATION_ERROR,
    RUNTIME_ERROR,
    SYSTEM_ERROR
}
