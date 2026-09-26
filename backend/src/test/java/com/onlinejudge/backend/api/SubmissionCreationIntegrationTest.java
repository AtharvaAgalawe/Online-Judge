package com.onlinejudge.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.backend.repository.ExecutionJobRepository;
import com.onlinejudge.backend.repository.LanguageRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.SubmissionStatus;

/**
 * Phase 5 DoD: submission row + execution job persist atomically, duplicate
 * Idempotency-Key returns the original submission, and the rate limit rejects rapid
 * repeats. Docker-backed (Postgres + Redis); runs in CI.
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SubmissionCreationIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    // Redis has no official Testcontainers module in the pinned BOM; the generic container
    // plus dynamic properties keeps the dependency surface minimal.
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
    private SubmissionRepository submissionRepository;

    @Autowired
    private ExecutionJobRepository executionJobRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createReturns202AndPersistsSubmissionAndJob() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String token = userToken("sub" + stamp);
        long problemId = seedPublishedProblem(stamp);
        long languageId = seedLanguage(stamp);

        String body = mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "problemId", problemId, "languageId", languageId,
                                "sourceCode", "class Main {}"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();

        long submissionId = objectMapper.readTree(body).get("submissionId").asLong();
        Submission submission = submissionRepository.findById(submissionId).orElseThrow();
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getSourceCode()).isEqualTo("class Main {}");
        assertThat(executionJobRepository.findBySubmissionId(submissionId)).isPresent();
    }

    @Test
    void duplicateIdempotencyKeyReturnsTheSameSubmission() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String token = userToken("sub" + stamp);
        long problemId = seedPublishedProblem(stamp);
        long languageId = seedLanguage(stamp);
        String key = "itest-" + stamp;
        String payload = objectMapper.writeValueAsString(Map.of(
                "problemId", problemId, "languageId", languageId, "sourceCode", "class Main {}"));

        String first = mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        long firstId = objectMapper.readTree(first).get("submissionId").asLong();
        long secondId = objectMapper.readTree(second).get("submissionId").asLong();
        assertThat(secondId).isEqualTo(firstId);

        User user = userRepository.findByUsername("sub" + stamp).orElseThrow();
        assertThat(submissionRepository.findByIdempotencyKeyAndUserId(key, user.getId()))
                .isPresent()
                .get()
                .extracting(Submission::getId)
                .isEqualTo(firstId);
    }

    @Test
    void rapidSecondSubmissionIsRateLimitedWithRetryAfter() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String token = userToken("sub" + stamp);
        long problemId = seedPublishedProblem(stamp);
        long languageId = seedLanguage(stamp);
        String payload = objectMapper.writeValueAsString(Map.of(
                "problemId", problemId, "languageId", languageId, "sourceCode", "class Main {}"));

        mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void submissionToUnpublishedProblemIs404() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String token = userToken("sub" + stamp);
        long problemId = problemRepository.saveAndFlush(new Problem("draft-" + stamp, "Draft " + stamp,
                "statement", Difficulty.EASY, 1000, 65536, null)).getId();
        long languageId = seedLanguage(stamp);

        performExpecting(token, problemId, languageId, "class Main {}", 404);
    }

    @Test
    void submissionToUnknownLanguageIs404() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String token = userToken("sub" + stamp);
        long problemId = seedPublishedProblem(stamp);

        performExpecting(token, problemId, 999_999L, "class Main {}", 404);
    }

    @Test
    void oversizedSourceIs400() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String token = userToken("sub" + stamp);
        long problemId = seedPublishedProblem(stamp);
        long languageId = seedLanguage(stamp);

        performExpecting(token, problemId, languageId, "a".repeat(65_537), 400);
    }

    private void performExpecting(String token, long problemId, long languageId, String sourceCode,
                                  int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "problemId", problemId, "languageId", languageId,
                                "sourceCode", sourceCode))))
                .andExpect(status().is(expectedStatus));
    }

    private long seedPublishedProblem(String stamp) {
        Problem problem = new Problem("slug-" + stamp, "Title " + stamp, "statement", Difficulty.EASY,
                1000, 65536, null);
        problem.setPublished(true);
        return problemRepository.saveAndFlush(problem).getId();
    }

    private long seedLanguage(String stamp) {
        return languageRepository.saveAndFlush(new Language("Java " + stamp, "Main.java",
                "javac Main.java", "java Main", "oj-java21", BigDecimal.ONE)).getId();
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
