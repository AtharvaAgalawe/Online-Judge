package com.onlinejudge.worker.execution;

/**
 * Result of the compile step.
 *
 * @param success        whether the compiler exited cleanly
 * @param compilerOutput combined stdout/stderr, truncated for the user (PRD §17: 4KB)
 */
public record CompileOutcome(boolean success, String compilerOutput) {
}
