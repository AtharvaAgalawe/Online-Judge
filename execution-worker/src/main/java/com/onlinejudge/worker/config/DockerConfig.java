package com.onlinejudge.worker.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.time.Duration;

/**
 * The execution-worker is the only component with Docker socket access (AGENTS.md §2).
 * Connections are lazy — the client can be built even when the daemon is briefly down;
 * the first sandbox command will fail loudly instead of blocking startup.
 */
@Configuration
public class DockerConfig {

    @Bean(destroyMethod = "close")
    DockerClient dockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(16)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofMinutes(2))
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    /** Threads that force-kill sandbox containers which outlive their wall-clock budget. */
    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService sandboxWatchdog() {
        return Executors.newScheduledThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "sandbox-watchdog");
            thread.setDaemon(true);
            return thread;
        });
    }
}
