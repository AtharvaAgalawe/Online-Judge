package com.onlinejudge.worker.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Shared entities come from the {@code common} module (AGENTS.md §2); the worker scans
 * its own repositories. The worker never migrates the schema — that is the backend's job.
 */
@Configuration
@EntityScan(basePackages = "com.onlinejudge.common.entity")
@EnableJpaRepositories(basePackages = "com.onlinejudge.worker.repository")
public class PersistenceConfig {
}
