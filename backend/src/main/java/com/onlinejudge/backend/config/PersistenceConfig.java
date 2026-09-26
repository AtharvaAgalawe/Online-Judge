package com.onlinejudge.backend.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Shared entities live in the {@code common} module so backend and worker can never
 * drift apart on the schema (AGENTS.md §2); backend-only entities sit in
 * {@code backend.domain}. Each module scans its own repositories.
 */
@Configuration
@EntityScan(basePackages = {"com.onlinejudge.common.entity", "com.onlinejudge.backend.domain"})
@EnableJpaRepositories(basePackages = "com.onlinejudge.backend.repository")
public class PersistenceConfig {
}
