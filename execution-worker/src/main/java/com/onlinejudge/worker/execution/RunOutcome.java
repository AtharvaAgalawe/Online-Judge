package com.onlinejudge.worker.execution;

/**
 * Raw result of one run-container execution. The verdict engine interprets these fields
 * (PRD §17): {@code timedOut} → TLE; exit 137 without a timeout → memory kill; non-zero
 * exit → runtime error; {@code stdoutTruncated} → presentation failure.
 *
 * @param exitCode        process exit code, -1 when it could not be determined
 * @param timedOut        the external wall-clock watchdog killed the container
 * @param stdoutTruncated captured stdout hit the hard cap (PRD §18 step 4)
 * @param stdout          captured stdout (possibly truncated)
 * @param stderr          captured stderr (possibly truncated)
 * @param durationMillis  wall-clock time from start to process exit
 */
public record RunOutcome(
        int exitCode,
        boolean timedOut,
        boolean stdoutTruncated,
        String stdout,
        String stderr,
        long durationMillis) {
}
