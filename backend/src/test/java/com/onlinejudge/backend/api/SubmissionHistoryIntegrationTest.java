package com.onlinejudge.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.onlinejudge.backend.repository.RoleRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.User;

/**
 * Phase 10 DoD: ownership checks are enforced — user B can never read user A's submission
 * or source code (403), while the owner and admins can; the history list only ever
 * contains the caller's own submissions unless the caller is an admin. Docker-backed.
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SubmissionHistoryIntegrationTest {

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

    @Test
    void ownershipIsEnforcedEverywhere() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("hadmin" + stamp);
        String alice = userToken("halice" + stamp);
        String bob = userToken("hbob" + stamp);

        JsonNode created = createPublishedProblem(admin, "History Problem " + stamp);
        long problemId = created.get("id").asLong();
        long languageId = 1L;

        String submission = mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "problemId", problemId, "languageId", languageId,
                                "sourceCode", "print('secret-source')"))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        long submissionId = objectMapper.readTree(submission).get("submissionId").asLong();

        // The DoD: user B can never read user A's submission or source code.
        mockMvc.perform(get("/api/v1/submissions/{id}", submissionId)
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/submissions/{id}/status", submissionId)
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/submissions")
                        .param("userId", "1")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isForbidden());

        // B's history never contains A's submission.
        String bobHistory = mockMvc.perform(get("/api/v1/submissions")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(bobHistory).doesNotContain("\"id\":" + submissionId);

        // The owner sees the full detail, including their own source code.
        mockMvc.perform(get("/api/v1/submissions/{id}", submissionId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCode").value("print('secret-source')"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.verdict").value(nullValue()))
                .andExpect(jsonPath("$.results").isArray());

        // The lightweight status endpoint answers for the owner.
        mockMvc.perform(get("/api/v1/submissions/{id}/status", submissionId)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // An admin may read any submission.
        mockMvc.perform(get("/api/v1/submissions/{id}", submissionId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCode").value("print('secret-source')"));

        // The owner's history contains it.
        String aliceHistory = mockMvc.perform(get("/api/v1/submissions")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(aliceHistory).contains("\"id\":" + submissionId);
    }

    private JsonNode createPublishedProblem(String adminToken, String title) throws Exception {
        String created = mockMvc.perform(post("/api/v1/admin/problems")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title, "statement", "s", "difficulty", "EASY"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long problemId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/v1/admin/problems/{id}/test-cases", problemId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "input", "1", "expectedOutput", "1"))))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/admin/problems/{id}", problemId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"published\": true}"))
                .andExpect(status().isOk());
        return objectMapper.readTree(created);
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
