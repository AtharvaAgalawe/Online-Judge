package com.onlinejudge.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.onlinejudge.common.entity.User;

/**
 * Phase 2 DoD (PRD roadmap): proves the Flyway-managed schema is real — the unique
 * username constraint actually fires. Docker-backed (Testcontainers): runs in CI via
 * the docker-tests profile, excluded from the fast local suite without Docker.
 */
@Tag("docker")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class UserRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserRepository userRepository;

    @Test
    void uniqueUsernameConstraintFires() {
        userRepository.saveAndFlush(new User("alice", "alice@example.com", "hash-not-a-real-secret"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                        new User("alice", "other@example.com", "hash-not-a-real-secret")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migrationsAppliedAndEntityPersistable() {
        User saved = userRepository.saveAndFlush(new User("bob", "bob@example.com", "hash-not-a-real-secret"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }
}
