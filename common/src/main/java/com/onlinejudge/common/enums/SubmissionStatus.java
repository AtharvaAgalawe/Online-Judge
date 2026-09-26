package com.onlinejudge.common.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline stage of a submission (PRD §17). The transition graph below is the single
 * source of truth for legal transitions (AGENTS.md §6) — nothing outside this enum
 * decides whether a status change is allowed.
 *
 * <p>Self-transitions are always permitted: at-least-once queue delivery can re-deliver
 * a job that is already being processed, and the worker re-enters the same stage
 * idempotently.
 */
public enum SubmissionStatus {
    SUBMITTED,
    QUEUED,
    PICKED_UP,
    COMPILING,
    RUNNING,
    EVALUATING,
    COMPLETED,
    COMPILATION_ERROR,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    SYSTEM_ERROR;

    /**
     * Edges of the lifecycle graph from PRD §17. Interpretation for PICKED_UP → RUNNING:
     * interpreted languages (compile_cmd IS NULL) skip the COMPILING stage.
     */
    private static final Map<SubmissionStatus, Set<SubmissionStatus>> TRANSITIONS = Map.of(
            SUBMITTED, EnumSet.of(QUEUED, SYSTEM_ERROR),
            QUEUED, EnumSet.of(PICKED_UP, SYSTEM_ERROR),
            PICKED_UP, EnumSet.of(COMPILING, RUNNING, SYSTEM_ERROR),
            COMPILING, EnumSet.of(RUNNING, COMPILATION_ERROR, SYSTEM_ERROR),
            RUNNING, EnumSet.of(EVALUATING, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED,
                    RUNTIME_ERROR, SYSTEM_ERROR),
            EVALUATING, EnumSet.of(COMPLETED, SYSTEM_ERROR));

    public boolean canTransitionTo(SubmissionStatus target) {
        return this == target || allowedTargets().contains(target);
    }

    private Set<SubmissionStatus> allowedTargets() {
        return TRANSITIONS.getOrDefault(this, EnumSet.noneOf(SubmissionStatus.class));
    }

    public boolean isTerminal() {
        return !TRANSITIONS.containsKey(this);
    }
}
