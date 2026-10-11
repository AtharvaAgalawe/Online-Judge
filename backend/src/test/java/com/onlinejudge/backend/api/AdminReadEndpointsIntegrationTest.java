package com.onlinejudge.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.backend.repository.ExecutionJobRepository;
import com.onlinejudge.backend.repository.LanguageRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.RoleRepository;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/** Phase 12 DoD: admin read endpoints are readable by admins and blocked for everyone else. */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AdminReadEndpointsIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ExecutionJobRepository executionJobRepository;

    @Test
    void adminCanListUnpublishedProblemsAndNonAdminCannot() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String user = userToken("usr" + stamp);
        String title = "Draft " + stamp;

        createProblem(admin, title);
        // Not published yet: visible to admin list, absent from the public list.
        mockMvc.perform(get("/api/v1/admin/problems").param("search", title)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].published").value(false))
                .andExpect(jsonPath("$.content[0].testCaseCount").value(0));
        mockMvc.perform(get("/api/v1/problems").param("search", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/problems").param("search", title)
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/problems").param("search", title))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanFetchProblemDetailIncludingPublishedFlag() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        JsonNode created = createProblem(admin, "Detail " + stamp);
        long id = created.get("id").asLong();

        mockMvc.perform(get("/api/v1/admin/problems/{id}", id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.createdBy").isNotEmpty());
    }

    @Test
    void adminProblemListAndDetailReportRealTestCaseCount() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String title = "Counted " + stamp;

        JsonNode created = createProblem(admin, title);
        long id = created.get("id").asLong();
        addTestCase(admin, id, Map.of("input", "1 2", "expectedOutput", "3", "isSample", true));
        addTestCase(admin, id, Map.of("input", "hidden-" + stamp, "expectedOutput", "42"));

        mockMvc.perform(get("/api/v1/admin/problems").param("search", title)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].testCaseCount").value(2));

        mockMvc.perform(get("/api/v1/admin/problems/{id}", id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCaseCount").value(2));
    }

    @Test
    void adminSubmissionsBrowserListsAllAndIsListable() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String user = userToken("usr" + stamp);

        mockMvc.perform(get("/api/v1/admin/submissions").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/v1/admin/submissions").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/v1/admin/submissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void queueStatusReturnsCountersAndIsAdminOnly() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String user = userToken("usr" + stamp);

        mockMvc.perform(get("/api/v1/admin/system/queue-status").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queued").isNumber())
                .andExpect(jsonPath("$.activeLease").isNumber())
                .andExpect(jsonPath("$.staleLease").isNumber())
                .andExpect(jsonPath("$.stale").isArray());

        mockMvc.perform(get("/api/v1/admin/system/queue-status").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void queueStatusCountsSeededLeasesWithCorrectSemantics() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        userToken("qusr" + stamp);
        User submitter = userRepository.findByUsername("qusr" + stamp).orElseThrow();
        Problem problem = problemRepository.findById(createProblem(admin, "Queue " + stamp).get("id").asLong())
                .orElseThrow();
        Language language = languageRepository.findByEnabledTrueOrderByNameAsc().get(0);

        JsonNode before = getQueueStatus(admin);
        long beforeQueued = before.get("queued").asLong();
        long beforeActive = before.get("activeLease").asLong();
        long beforeStale = before.get("staleLease").asLong();
        long beforeRetried = before.get("retried").asLong();

        Instant now = Instant.now();

        // Active lease, submission still non-terminal -> activeLease.
        Submission activeSubmission = seedSubmission(submitter, problem, language, stamp, "active", false);
        seedJob(activeSubmission, "worker-active", now, now.plusSeconds(3600), 0);

        // Stale lease, submission non-terminal -> staleLease + stale[].
        Submission staleSubmission = seedSubmission(submitter, problem, language, stamp, "stale", false);
        seedJob(staleSubmission, "worker-stale", now.minusSeconds(3600), now.minusSeconds(60), 0);

        // Stale lease but submission terminal -> excluded from staleLease and stale[].
        Submission completedSubmission = seedSubmission(submitter, problem, language, stamp, "done", true);
        seedJob(completedSubmission, "worker-done", now.minusSeconds(3600), now.minusSeconds(60), 0);

        // Retried job (no lease) -> retried only.
        Submission retriedSubmission = seedSubmission(submitter, problem, language, stamp, "retry", false);
        seedJob(retriedSubmission, null, null, null, 1);

        JsonNode after = getQueueStatus(admin);
        assertThat(after.get("queued").asLong()).isEqualTo(beforeQueued);
        assertThat(after.get("activeLease").asLong()).isEqualTo(beforeActive + 1);
        assertThat(after.get("staleLease").asLong()).isEqualTo(beforeStale + 1);
        assertThat(after.get("retried").asLong()).isEqualTo(beforeRetried + 1);

        boolean staleRowFound = false;
        boolean completedRowFound = false;
        for (JsonNode row : after.get("stale")) {
            long submissionId = row.get("submissionId").asLong();
            if (submissionId == staleSubmission.getId()) {
                staleRowFound = true;
                assertThat(row.get("status").asText()).isEqualTo(SubmissionStatus.RUNNING.name());
            }
            if (submissionId == completedSubmission.getId()) {
                completedRowFound = true;
            }
        }
        assertThat(staleRowFound).isTrue();
        assertThat(completedRowFound).isFalse();
    }

    private JsonNode getQueueStatus(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/system/queue-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Submission seedSubmission(User user, Problem problem, Language language, String stamp,
                                      String suffix, boolean terminal) {
        Submission submission = new Submission(user, problem, language, "class Main {}",
                "idem-" + stamp + "-" + suffix);
        submission.transitionTo(SubmissionStatus.QUEUED);
        submission.transitionTo(SubmissionStatus.PICKED_UP);
        submission.transitionTo(SubmissionStatus.RUNNING);
        if (terminal) {
            submission.transitionTo(SubmissionStatus.EVALUATING);
            submission.applyTerminalOutcome(SubmissionStatus.COMPLETED, Verdict.ACCEPTED, 1, 1, null, null);
        }
        return submissionRepository.saveAndFlush(submission);
    }

    private ExecutionJob seedJob(Submission submission, String lockedBy, Instant lockedAt,
                                 Instant leaseExpiresAt, int retryCount) {
        ExecutionJob job = new ExecutionJob(submission);
        ReflectionTestUtils.setField(job, "lockedBy", lockedBy);
        ReflectionTestUtils.setField(job, "lockedAt", lockedAt);
        ReflectionTestUtils.setField(job, "leaseExpiresAt", leaseExpiresAt);
        ReflectionTestUtils.setField(job, "retryCount", retryCount);
        return executionJobRepository.saveAndFlush(job);
    }

    private JsonNode createProblem(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/problems")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title, "statement", "s", "difficulty", "EASY"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode addTestCase(String token, long problemId, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/problems/{id}/test-cases", problemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String adminToken(String username) throws Exception {
        register(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        user.grantRole(roleRepository.findByName("ROLE_ADMIN").orElseThrow());
        userRepository.saveAndFlush(user);
        return login(username);
    }

    private String userToken(String username) throws Exception {
        register(username);
        return login(username);
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "email", username + "@example.com",
                                "password", "password123"))))
                .andExpect(status().isCreated());
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
