package com.onlinejudge.worker.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Worker settings (AGENTS.md §7: grouped configuration).
 *
 * @param leaseDuration   how long a claimed job stays leased before another worker may
 *                        steal it — the safety net for a worker that dies mid-job (PRD §17)
 * @param compileTimeout  hard wall-clock bound for the compile step (PRD §26)
 * @param watchdogBuffer  extra wall-clock allowance on top of the problem's time limit
 *                        before the external watchdog kills a run container (PRD §17)
 * @param maxOutputBytes  hard cap on captured stdout/stderr per run (PRD §18: 64KB)
 * @param compileMemoryMb memory limit for the compile container (PRD §18: 512MB)
 * @param pidsLimit       process/thread cap for every sandbox container (PRD §26)
 */
@ConfigurationProperties(prefix = "app.worker")
public record WorkerProperties(
        @DefaultValue("60s") Duration leaseDuration,
        @DefaultValue("10s") Duration compileTimeout,
        @DefaultValue("2s") Duration watchdogBuffer,
        @DefaultValue("65536") int maxOutputBytes,
        @DefaultValue("512") int compileMemoryMb,
        @DefaultValue("64") long pidsLimit) {
}
