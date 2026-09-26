package com.onlinejudge.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Submission-creation limits (PRD §26). Grouped configuration per AGENTS.md §7.
 *
 * @param rateLimitWindow  fixed window for the per-user submission rate limit (PRD: 1 per 3s)
 * @param idempotencyTtl   how long an Idempotency-Key is remembered (PRD §24: 24h)
 * @param maxSourceBytes   maximum submitted source size in UTF-8 bytes (PRD: 65KB)
 */
@ConfigurationProperties(prefix = "app.submission")
public record SubmissionProperties(
        @DefaultValue("3s") Duration rateLimitWindow,
        @DefaultValue("24h") Duration idempotencyTtl,
        @DefaultValue("65536") int maxSourceBytes) {
}
