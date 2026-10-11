package com.onlinejudge.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The codebase's first scheduled task (contest problem publication, PRD §7.8). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
