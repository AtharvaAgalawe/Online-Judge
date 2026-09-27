package com.onlinejudge.worker.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
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
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.messaging.MessagingTopology;
import com.onlinejudge.worker.repository.ExecutionJobRepository;

/**
 * Phase 9 DoD (PRD §20): a correct reference solution is ACCEPTED, an incorrect one is
 * WRONG_ANSWER against the right hidden test case, an infinite loop is
 * TIME_LIMIT_EXCEEDED — all judged through the real pipeline (queue → lease → sandbox →
 * verdict → transactional write → ack). Lease exclusivity and duplicate-delivery safety
 * remain covered. Docker-backed; runs in CI.
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

    private static String pythonImage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ExecutionJobRepository jobRepository;

    @BeforeAll
    static void buildLanguageImage() {
        pythonImage = new ImageFromDockerfile("oj-python312-itest", false)
                .withDockerfile(Path.of("../infrastructure/docker/images/oj-python312/Dockerfile"))
                .get();
    }

    @Test
    @Transactional
    void onlyOneWorkerWinsTheLeaseAndAnExpiredLeaseCountsARetry() {
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
    void correctSolutionIsAcceptedEndToEnd() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        long submissionId = seedJudgedSubmission(stamp, """
                a, b = map(int, input().split())
                print(a + b)
                """, 2_000, List.of(
                new TestCaseSeed("1 1", "2", true),
                new TestCaseSeed("2 3", "5", false)));

        publish(submissionId);
        Map<String, Object> submission = awaitStatus(submissionId, "COMPLETED");

        assertThat(submission.get("verdict")).isEqualTo("ACCEPTED");
        assertThat(submission.get("failed_test_case_id")).isNull();
        assertThat(submission.get("judged_at")).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM submission_results WHERE submission_id = ? AND verdict = 'ACCEPTED'",
                Integer.class, submissionId)).isEqualTo(2);
        awaitQueueDrained();
    }

    @Test
    void wrongAnswerOnHiddenTestIsReportedWithTheFailingTestCase() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        long submissionId = seedJudgedSubmission(stamp, """
                a, b = map(int, input().split())
                print(a + b if (a, b) == (1, 1) else 999)
                """, 2_000, List.of(
                new TestCaseSeed("1 1", "2", true),
                new TestCaseSeed("2 3", "5", false)));
        Long hiddenTestCaseId = jdbcTemplate.queryForObject(
                "SELECT id FROM test_cases WHERE problem_id = "
                        + "(SELECT problem_id FROM submissions WHERE id = ?) AND is_sample = false",
                Long.class, submissionId);

        publish(submissionId);
        Map<String, Object> submission = awaitStatus(submissionId, "COMPLETED");

        assertThat(submission.get("verdict")).isEqualTo("WRONG_ANSWER");
        assertThat(((Number) submission.get("failed_test_case_id")).longValue()).isEqualTo(hiddenTestCaseId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM submission_results WHERE submission_id = ?",
                Integer.class, submissionId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stdout_snippet FROM submission_results WHERE submission_id = ? AND test_case_id = ?",
                String.class, submissionId, hiddenTestCaseId))
                .as("hidden test output is never stored")
                .isNull();
    }

    @Test
    void infiniteLoopIsReportedAsTimeLimitExceeded() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        long submissionId = seedJudgedSubmission(stamp, """
                while True:
                    pass
                """, 500, List.of(new TestCaseSeed("", "", true)));

        publish(submissionId);
        Map<String, Object> submission = awaitStatus(submissionId, "TIME_LIMIT_EXCEEDED");

        assertThat(submission.get("verdict")).isEqualTo("TIME_LIMIT_EXCEEDED");
    }

    @Test
    void duplicateDeliveryDoesNotDoubleJudge() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        long submissionId = seedJudgedSubmission(stamp, """
                a, b = map(int, input().split())
                print(a + b)
                """, 2_000, List.of(new TestCaseSeed("1 1", "2", true)));
        SubmissionJobMessage message = new SubmissionJobMessage(submissionId, 1L, 1, 2_000, 65_536, List.of());

        publish(submissionId);
        Map<String, Object> completed = awaitStatus(submissionId, "COMPLETED");
        Object firstJudgedAt = completed.get("judged_at");

        // Same message delivered again: the lease is still held and the submission is
        // terminal — the worker must ack and discard without touching the verdict.
        rabbitTemplate.convertAndSend(MessagingTopology.EXCHANGE, MessagingTopology.ROUTING_KEY, message);
        Thread.sleep(2_000);

        Map<String, Object> afterDuplicate = querySubmission(submissionId);
        assertThat(afterDuplicate.get("status")).isEqualTo("COMPLETED");
        assertThat(afterDuplicate.get("verdict")).isEqualTo("ACCEPTED");
        assertThat(afterDuplicate.get("judged_at")).isEqualTo(firstJudgedAt);
        assertThat(jobRepository.findBySubmissionId(submissionId).orElseThrow().getRetryCount())
                .isEqualTo(1);
        awaitQueueDrained();
    }

    private void publish(long submissionId) {
        rabbitTemplate.convertAndSend(MessagingTopology.EXCHANGE, MessagingTopology.ROUTING_KEY,
                new SubmissionJobMessage(submissionId, 1L, 1, 2_000, 65_536, List.of()));
    }

    private void awaitQueueDrained() throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (System.currentTimeMillis() < deadline
                && rabbitAdmin.getQueueInfo(MessagingTopology.JOBS_QUEUE).getMessageCount() != 0) {
            Thread.sleep(200);
        }
        assertThat(rabbitAdmin.getQueueInfo(MessagingTopology.JOBS_QUEUE).getMessageCount()).isZero();
    }

    /** Seeds a Python submission with real test cases, ready to be judged. */
    private long seedJudgedSubmission(String stamp, String source, int timeLimitMs,
                                      List<TestCaseSeed> testCases) {
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, 'x') RETURNING id",
                Long.class, "j" + stamp, "j" + stamp + "@example.com");
        Long languageId = jdbcTemplate.queryForObject(
                "INSERT INTO languages (name, source_filename, compile_cmd, run_cmd, docker_image, "
                        + "time_limit_multiplier) VALUES (?, 'main.py', NULL, 'python3 main.py', ?, 1.0) "
                        + "RETURNING id",
                Long.class, "Python " + stamp, pythonImage);
        Long problemId = jdbcTemplate.queryForObject(
                "INSERT INTO problems (slug, title, statement, difficulty, time_limit_ms, memory_limit_kb, "
                        + "is_published) VALUES (?, ?, 's', 'EASY', ?, 65536, true) RETURNING id",
                Long.class, "p" + stamp, "P " + stamp, timeLimitMs);
        for (int i = 0; i < testCases.size(); i++) {
            TestCaseSeed testCase = testCases.get(i);
            jdbcTemplate.update(
                    "INSERT INTO test_cases (problem_id, input, expected_output, is_sample, display_order) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    problemId, testCase.input(), testCase.expected(), testCase.sample(), i);
        }
        Long submissionId = jdbcTemplate.queryForObject(
                "INSERT INTO submissions (user_id, problem_id, language_id, source_code, status) "
                        + "VALUES (?, ?, ?, ?, 'QUEUED') RETURNING id",
                Long.class, userId, problemId, languageId, source);
        jdbcTemplate.update("INSERT INTO execution_jobs (submission_id) VALUES (?)", submissionId);
        return submissionId;
    }

    /** Seeds a bare submission/job pair for lease tests (never judged). */
    private long seedSubmissionWithJob(String label) {
        String stamp = System.nanoTime() + label;
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, 'x') RETURNING id",
                Long.class, "w" + stamp, "w" + stamp + "@example.com");
        Long languageId = jdbcTemplate.queryForObject(
                "INSERT INTO languages (name, source_filename, compile_cmd, run_cmd, docker_image, "
                        + "time_limit_multiplier) VALUES (?, 'main.py', NULL, 'python3 main.py', ?, 1.0) "
                        + "RETURNING id",
                Long.class, "Python " + stamp, pythonImage);
        Long problemId = jdbcTemplate.queryForObject(
                "INSERT INTO problems (slug, title, statement, difficulty, is_published) "
                        + "VALUES (?, ?, 's', 'EASY', true) RETURNING id",
                Long.class, "p" + stamp, "P " + stamp);
        Long submissionId = jdbcTemplate.queryForObject(
                "INSERT INTO submissions (user_id, problem_id, language_id, source_code, status) "
                        + "VALUES (?, ?, ?, 'print(1)', 'QUEUED') RETURNING id",
                Long.class, userId, problemId, languageId);
        jdbcTemplate.update("INSERT INTO execution_jobs (submission_id) VALUES (?)", submissionId);
        return submissionId;
    }

    private Map<String, Object> querySubmission(long submissionId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, verdict, judged_at, failed_test_case_id FROM submissions WHERE id = ?",
                submissionId);
    }

    private Map<String, Object> awaitStatus(long submissionId, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(25);
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

    private record TestCaseSeed(String input, String expected, boolean sample) {
    }
}
