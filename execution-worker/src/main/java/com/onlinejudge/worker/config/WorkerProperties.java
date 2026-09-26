package com.onlinejudge.worker.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Worker settings (AGENTS.md §7: grouped configuration).
 *
 * @param leaseDuration how long a claimed job stays leased before another worker may
 *                      steal it — the safety net for a worker that dies mid-job (PRD §17)
 */
@ConfigurationProperties(prefix = "app.worker")
public record WorkerProperties(@DefaultValue("60s") Duration leaseDuration) {
}
