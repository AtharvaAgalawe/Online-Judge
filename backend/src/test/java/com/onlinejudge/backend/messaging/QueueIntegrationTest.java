package com.onlinejudge.backend.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.backend.repository.LanguageRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.backend.service.SubmissionCreatedEvent;
import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.TestCase;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.SubmissionStatus;

/**
 * Phase 6 DoD: the job message is published only after the submission transaction
 * commits, never on rollback, and the submission becomes QUEUED once its message is on
 * the queue. Docker-backed (Postgres + RabbitMQ + Redis); runs in CI.
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class QueueIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Test
    void topologyDeclaresJobsRetryAndDeadLetterQueues() {
        assertThat(rabbitAdmin.getQueueInfo(RabbitTopologyConfig.JOBS_QUEUE)).isNotNull();
        assertThat(rabbitAdmin.getQueueInfo(RabbitTopologyConfig.RETRY_QUEUE)).isNotNull();
        assertThat(rabbitAdmin.getQueueInfo(RabbitTopologyConfig.DEAD_LETTER_QUEUE)).isNotNull();
    }

    @Test
    void jobIsPublishedAfterCommitAndSubmissionBecomesQueued() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String token = userToken("queue" + stamp);
        Problem problem = problemRepository.saveAndFlush(new Problem("qslug-" + stamp, "Q " + stamp,
                "statement", Difficulty.EASY, 1000, 65_536, null));
        problem.setPublished(true);
        problemRepository.saveAndFlush(problem);
        TestCase testCase = new TestCase("1 2", "3", true, 0, 1);
        problem.addTestCase(testCase);
        testCaseRepository.saveAndFlush(testCase);
        Long languageId = languageRepository.saveAndFlush(new Language("Java " + stamp, "Main.java",
                "javac Main.java", "java Main", "oj-java21", BigDecimal.ONE)).getId();

        String body = mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "problemId", problem.getId(), "languageId", languageId,
                                "sourceCode", "class Main {}"))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        long submissionId = objectMapper.readTree(body).get("submissionId").asLong();

        Object received = rabbitTemplate.receiveAndConvert(RabbitTopologyConfig.JOBS_QUEUE, 5_000);
        assertThat(received).isInstanceOf(SubmissionJobMessage.class);
        SubmissionJobMessage message = (SubmissionJobMessage) received;
        assertThat(message.submissionId()).isEqualTo(submissionId);
        assertThat(message.testCaseIds()).containsExactly(testCase.getId());
        assertThat(message.timeLimitMs()).isEqualTo(1000);

        SubmissionStatus status = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            status = submissionRepository.findById(submissionId).map(Submission::getStatus).orElse(null);
            if (status == SubmissionStatus.QUEUED) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(status).isEqualTo(SubmissionStatus.QUEUED);
    }

    @Test
    void rolledBackTransactionNeverPublishesButCommittedOneDoes() {
        SubmissionJobMessage message = new SubmissionJobMessage(999L, 1L, 1, 1000, 65_536, List.of());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new SubmissionCreatedEvent(message, "cid-rollback"));
            status.setRollbackOnly();
        });
        assertThat(rabbitTemplate.receiveAndConvert(RabbitTopologyConfig.JOBS_QUEUE, 1_500)).isNull();

        transaction.executeWithoutResult(status ->
                eventPublisher.publishEvent(new SubmissionCreatedEvent(message, "cid-commit")));
        assertThat(rabbitTemplate.receiveAndConvert(RabbitTopologyConfig.JOBS_QUEUE, 5_000)).isNotNull();
    }

    private String userToken(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "email", username + "@example.com",
                                "password", "password123"))))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(login).get("accessToken").asText();
    }
}
