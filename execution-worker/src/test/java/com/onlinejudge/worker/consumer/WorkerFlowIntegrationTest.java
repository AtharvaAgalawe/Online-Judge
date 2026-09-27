package com.onlinejudge.worker.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.messaging.MessagingTopology;
import com.onlinejudge.worker.repository.ExecutionJobRepository;

/**
 * Phase 7 DoD: duplicate deliveries cannot double-process a job (lease exclusivity) and
 * the full SUBMITTED → QUEUED → PICKED_UP → COMPLETED pipeline runs through the real
 * broker. Docker-backed (Postgres + RabbitMQ); runs in CI.
 */
@Tag("docker")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class WorkerFlowIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ExecutionJobRepository jobRepository;

    @Test
    @Transactional
    void onlyOneWorkerWinsTheLeaseAndAnExpiredLeaseCountsARetry() {
        // The lease queries are @Modifying and demand an active transaction, exactly like
        // their production caller (JobClaimService.claim); the test rolls back after.
        long submissionId = seedSubmissionWithJob("lease");

        assertThat(jobRepository.tryAcquireLease(submissionId, "worker-A", 60)).isEqualTo(1);
        assertThat(jobRepository.tryAcquireLease(submissionId, "worker-B", 60)).isZero();

        assertThat(jobRepository.findBySubmissionId(submissionId).orElseThrow().getLockedBy())
                .isEqualTo("worker-A");
        assertThat(jobRepository.findBySubmissionId(submissionId).orElseThrow().getRetryCount())
                .isEqualTo(1);

        jdbcTemplate.update(
                "UPDATE execution_jobs SET lease_expires_at = now() - interval '1 second' WHERE submission_id = ?",
                submissionId);

        assertThat(jobRepository.tryAcquireLease(submissionId, "worker-B", 60)).isEqualTo(1);
        var job = jobRepository.findBySubmissionId(submissionId).orElseThrow();
        assertThat(job.getLockedBy()).isEqualTo("worker-B");
        assertThat(job.getRetryCount()).isEqualTo(2);
    }

    @Test
    void jobIsConsumedLeasedJudgedAndAcked() throws Exception {
        long submissionId = seedSubmissionWithJob("e2e");

        rabbitTemplate.convertAndSend(MessagingTopology.EXCHANGE, MessagingTopology.ROUTING_KEY,
                new SubmissionJobMessage(submissionId, 1L, 1, 1_000, 65_536, List.of()));

        var submission = awaitStatus(submissionId, "COMPLETED");
        assertThat(submission.get("verdict")).isEqualTo("ACCEPTED");
        assertThat(submission.get("judged_at")).isNotNull();

        // Manual ack: the message must leave the queue after the terminal write commits.
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        Integer messages = null;
        while (System.currentTimeMillis() < deadline) {
            messages = rabbitAdmin.getQueueInfo(MessagingTopology.JOBS_QUEUE).getMessageCount();
            if (messages != null && messages == 0) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat(messages).isZero();
    }

    @Test
    void duplicateDeliveryDoesNotDoubleJudge() throws Exception {
        long submissionId = seedSubmissionWithJob("dup");
        SubmissionJobMessage message = new SubmissionJobMessage(submissionId, 1L, 1, 1_000, 65_536, List.of());

        rabbitTemplate.convertAndSend(MessagingTopology.EXCHANGE, MessagingTopology.ROUTING_KEY, message);
        var completed = awaitStatus(submissionId, "COMPLETED");
        Object firstJudgedAt = completed.get("judged_at");

        // Same message delivered again: the lease is still held and the submission is
        // terminal — the worker must ack and discard without touching the verdict.
        rabbitTemplate.convertAndSend(MessagingTopology.EXCHANGE, MessagingTopology.ROUTING_KEY, message);
        Thread.sleep(2_000);

        var afterDuplicate = querySubmission(submissionId);
        assertThat(afterDuplicate.get("status")).isEqualTo("COMPLETED");
        assertThat(afterDuplicate.get("verdict")).isEqualTo("ACCEPTED");
        assertThat(afterDuplicate.get("judged_at")).isEqualTo(firstJudgedAt);
        assertThat(jobRepository.findBySubmissionId(submissionId).orElseThrow().getRetryCount())
                .isEqualTo(1);

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (System.currentTimeMillis() < deadline
                && rabbitAdmin.getQueueInfo(MessagingTopology.JOBS_QUEUE).getMessageCount() != 0) {
            Thread.sleep(200);
        }
        assertThat(rabbitAdmin.getQueueInfo(MessagingTopology.JOBS_QUEUE).getMessageCount()).isZero();
    }

    private long seedSubmissionWithJob(String label) {
        String stamp = System.nanoTime() + label;
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, 'x') RETURNING id",
                Long.class, "w" + stamp, "w" + stamp + "@example.com");
        Long languageId = jdbcTemplate.queryForObject(
                "INSERT INTO languages (name, source_filename, compile_cmd, run_cmd, docker_image, "
                        + "time_limit_multiplier) VALUES (?, 'Main.java', 'javac Main.java', 'java Main', "
                        + "'oj-java21', 1.0) RETURNING id",
                Long.class, "Java " + stamp);
        Long problemId = jdbcTemplate.queryForObject(
                "INSERT INTO problems (slug, title, statement, difficulty, is_published) "
                        + "VALUES (?, ?, 's', 'EASY', true) RETURNING id",
                Long.class, "p" + stamp, "P " + stamp);
        Long submissionId = jdbcTemplate.queryForObject(
                "INSERT INTO submissions (user_id, problem_id, language_id, source_code, status) "
                        + "VALUES (?, ?, ?, 'class Main {}', 'QUEUED') RETURNING id",
                Long.class, userId, problemId, languageId);
        jdbcTemplate.update("INSERT INTO execution_jobs (submission_id) VALUES (?)", submissionId);
        return submissionId;
    }

    private Map<String, Object> querySubmission(long submissionId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, verdict, judged_at FROM submissions WHERE id = ?", submissionId);
    }

    private Map<String, Object> awaitStatus(long submissionId, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
        Map<String, Object> row = null;
        while (System.currentTimeMillis() < deadline) {
            row = querySubmission(submissionId);
            if (expected.equals(row.get("status"))) {
                return row;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Submission %d did not reach %s (last: %s)"
                .formatted(submissionId, expected, row));
    }
}
