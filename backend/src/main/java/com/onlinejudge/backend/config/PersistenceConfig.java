package com.onlinejudge.backend.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entities live in the shared {@code common} module so backend and worker can never
 * drift apart on the schema (AGENTS.md §2); each module scans its own repositories.
 */
@Configuration
@EntityScan(basePackages = "com.onlinejudge.common.entity")
@EnableJpaRepositories(basePackages = "com.onlinejudge.backend.repository")
public class PersistenceConfig {
}
