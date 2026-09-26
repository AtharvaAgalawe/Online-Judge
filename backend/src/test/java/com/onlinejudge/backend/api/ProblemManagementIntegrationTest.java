package com.onlinejudge.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.backend.repository.LanguageRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.RoleRepository;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.SubmissionResultRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.SubmissionResult;
import com.onlinejudge.common.entity.TestCase;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Verdict;

/**
 * Phase 4 DoD: admin authors and publishes problems; unpublished problems are invisible
 * to solvers even via direct slug; hidden test content is never exposed. Docker-backed.
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ProblemManagementIntegrationTest {

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
    private TestCaseRepository testCaseRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SubmissionResultRepository submissionResultRepository;

    @Test
    void adminAuthoringAndPublicVisibility() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String title = "Two Sum " + stamp;

        JsonNode created = createProblem(admin, title, Map.of("tags", List.of("arrays")));
        long problemId = created.get("id").asLong();
        String slug = created.get("slug").asText();
        assertThat(slug).isEqualTo("two-sum-" + stamp);

        addTestCase(admin, problemId, Map.of("input", "1 2", "expectedOutput", "3", "isSample", true));
        addTestCase(admin, problemId, Map.of("input", "hidden-" + stamp, "expectedOutput", "42"));

        mockMvc.perform(put("/api/v1/admin/problems/{id}", problemId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"published\": true}"))
                .andExpect(status().isOk());

        String listBody = mockMvc.perform(get("/api/v1/problems").param("search", title))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listBody).contains(slug);
        assertThat(listBody).contains("\"acceptanceRate\":0.0");

        String detailBody = mockMvc.perform(get("/api/v1/problems/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleTestCases.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(detailBody).contains("1 2");
        assertThat(detailBody).doesNotContain("hidden-" + stamp);

        mockMvc.perform(delete("/api/v1/admin/problems/{id}", problemId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/problems/{slug}", slug)).andExpect(status().isNotFound());
        String afterUnpublish = mockMvc.perform(get("/api/v1/problems").param("search", title))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(afterUnpublish).doesNotContain(slug);
    }

    @Test
    void publishingWithoutTestCasesIs400() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        JsonNode created = createProblem(admin, "Empty Problem " + stamp, Map.of());

        mockMvc.perform(put("/api/v1/admin/problems/{id}", created.get("id").asLong())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"published\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void nonAdminCannotAuthorProblems() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String user = userToken("usr" + stamp);

        mockMvc.perform(post("/api/v1/admin/problems")
                        .header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Nope", "statement", "s", "difficulty", "EASY"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateTitleGetsSuffixedSlug() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String title = "Same Title " + stamp;

        createProblem(admin, title, Map.of());
        JsonNode second = createProblem(admin, title, Map.of());
        assertThat(second.get("slug").asText()).endsWith("-2");
    }

    @Test
    void deletingTestCaseReferencedByJudgedHistoryIs409() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        JsonNode created = createProblem(admin, "Judged Problem " + stamp, Map.of());
        long problemId = created.get("id").asLong();
        JsonNode testCase = addTestCase(admin, problemId,
                Map.of("input", "in", "expectedOutput", "out"));
        long testCaseId = testCase.get("id").asLong();

        User user = userRepository.findByUsername("adm" + stamp).orElseThrow();
        Language language = languageRepository.saveAndFlush(new Language(
                "Java " + stamp, "Main.java", "javac Main.java", "java Main", "oj-java21", BigDecimal.ONE));
        var problem = problemRepository.findById(problemId).orElseThrow();
        Submission submission = submissionRepository.saveAndFlush(
                new Submission(user, problem, language, "class Main {}", null));
        TestCase referenced = testCaseRepository.findById(testCaseId).orElseThrow();
        submissionResultRepository.saveAndFlush(
                new SubmissionResult(submission, referenced, Verdict.WRONG_ANSWER, 5, 512, null));

        mockMvc.perform(delete("/api/v1/admin/problems/{id}/test-cases/{tcId}", problemId, testCaseId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private JsonNode createProblem(String token, String title, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        body.put("statement", "Given two numbers, print their sum.");
        body.put("difficulty", "EASY");
        body.putAll(extra);
        String response = mockMvc.perform(post("/api/v1/admin/problems")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
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
