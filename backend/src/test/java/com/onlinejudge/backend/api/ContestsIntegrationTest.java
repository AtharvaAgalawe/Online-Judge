package com.onlinejudge.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.backend.repository.LanguageRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.RoleRepository;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.backend.service.ContestPublicationService;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/**
 * Contests end-to-end (PRD §7.8): admin authoring with inline problems, hidden-until-published
 * visibility, contest problem access for registered participants, the registration gate on
 * submissions, the end-of-contest publish job (invoked directly), and standings computed
 * through the real {@code findForStandings} query. Docker-backed; runs in CI.
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ContestsIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    // Standings go through the Redis cache-aside; the generic container keeps the dependency
    // surface minimal (same pattern as SubmissionCreationIntegrationTest).
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
    private ContestRepository contestRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ContestPublicationService contestPublicationService;

    @Test
    void contestProblemLifecycleFromCreationToPublication() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String participant = userToken("usr" + stamp);
        String outsider = userToken("out" + stamp);
        String name = "Lifecycle " + stamp;
        String title = "Lifecycle Problem " + stamp;

        long contestId = createUpcomingContest(admin, name, false, null);
        JsonNode problem = createContestProblem(admin, contestId, title, 100);
        long problemId = problem.get("problemId").asLong();
        String slug = problem.get("slug").asText();
        addTestCase(admin, problemId, Map.of("input", "1 2", "expectedOutput", "3", "isSample", true));
        registerForContest(participant, contestId);
        updateContestWindow(admin, contestId, name, false, null,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/problems").param("search", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/contests/{id}", contestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problemsRevealed").value(true))
                .andExpect(jsonPath("$.problems.length()").value(1))
                .andExpect(jsonPath("$.problems[0].slug").value(slug));

        mockMvc.perform(get("/api/v1/contests/{id}/problems/{slug}", contestId, slug)
                        .header("Authorization", "Bearer " + participant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.contestId").value(contestId));

        mockMvc.perform(get("/api/v1/contests/{id}/problems/{slug}", contestId, slug)
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
    }

    @Test
    void unregisteredSubmissionDuringContestIs403() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String outsider = userToken("out" + stamp);
        long languageId = seedLanguage(stamp).getId();

        long contestId = createUpcomingContest(admin, "Gate " + stamp, false, null);
        JsonNode problem = createContestProblem(admin, contestId, "Gate Problem " + stamp, 100);
        updateContestWindow(admin, contestId, "Gate " + stamp, false, null,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));

        submit(outsider, problem.get("problemId").asLong(), languageId)
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Register for this contest to submit"));
    }

    @Test
    void registeredSubmissionDuringContestIsAccepted() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String participant = userToken("usr" + stamp);
        long languageId = seedLanguage(stamp).getId();

        long contestId = createUpcomingContest(admin, "Submit " + stamp, false, null);
        JsonNode problem = createContestProblem(admin, contestId, "Submit Problem " + stamp, 100);
        long problemId = problem.get("problemId").asLong();
        addTestCase(admin, problemId, Map.of("input", "1 2", "expectedOutput", "3"));
        registerForContest(participant, contestId);
        updateContestWindow(admin, contestId, "Submit " + stamp, false, null,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));

        submit(participant, problemId, languageId).andExpect(status().isAccepted());
    }

    @Test
    void jobPublishesProblemsWhenContestEnds() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String name = "Ended " + stamp;
        String title = "Ended Problem " + stamp;

        long contestId = createUpcomingContest(admin, name, false, null);
        JsonNode problem = createContestProblem(admin, contestId, title, 100);
        long problemId = problem.get("problemId").asLong();
        String slug = problem.get("slug").asText();
        addTestCase(admin, problemId, Map.of("input", "1 2", "expectedOutput", "3"));

        Instant now = Instant.now();
        updateContestWindow(admin, contestId, name, false, null,
                now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));

        contestPublicationService.publishContest(contestId);

        mockMvc.perform(get("/api/v1/problems").param("search", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].slug").value(slug));
        mockMvc.perform(get("/api/v1/problems/{slug}", slug)).andExpect(status().isOk());

        Instant marker = contestRepository.findById(contestId).orElseThrow().getProblemsPublishedAt();
        assertThat(marker).isNotNull();

        contestPublicationService.publishContest(contestId);
        assertThat(contestRepository.findById(contestId).orElseThrow().getProblemsPublishedAt())
                .isEqualTo(marker);
    }

    @Test
    void privateContestIsInvisibleAndNeverPublishes() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String name = "Private " + stamp;
        String title = "Private Problem " + stamp;

        long contestId = createUpcomingContest(admin, name, true, null);
        JsonNode problem = createContestProblem(admin, contestId, title, 100);
        String slug = problem.get("slug").asText();
        addTestCase(admin, problem.get("problemId").asLong(), Map.of("input", "1 2", "expectedOutput", "3"));

        Instant now = Instant.now();
        updateContestWindow(admin, contestId, name, true, null,
                now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));

        String browseBody = mockMvc.perform(get("/api/v1/contests"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(browseBody).doesNotContain(name);

        mockMvc.perform(get("/api/v1/contests/{id}", contestId)).andExpect(status().isNotFound());

        contestPublicationService.publishContest(contestId);
        assertThat(problemRepository.findBySlug(slug).orElseThrow().isPublished()).isFalse();
        assertThat(contestRepository.findById(contestId).orElseThrow().getProblemsPublishedAt()).isNull();
    }

    @Test
    void standingsReflectSubmissions() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String username = "usr" + stamp;
        String participant = userToken(username);
        Language language = seedLanguage(stamp);
        String name = "Standings " + stamp;

        long contestId = createUpcomingContest(admin, name, false, 0);
        JsonNode problem = createContestProblem(admin, contestId, "Standings Problem " + stamp, 250);
        long problemId = problem.get("problemId").asLong();
        registerForContest(participant, contestId);

        User user = userRepository.findByUsername(username).orElseThrow();
        Problem problemEntity = problemRepository.findById(problemId).orElseThrow();
        seedTerminalSubmission(user, problemEntity, language, Verdict.WRONG_ANSWER, "wa-" + stamp);
        // Distinct submitted_at values are required for "wrong attempt before the first AC";
        // the DB clock is millisecond-resolution, so nudge the accepted insert past it.
        Thread.sleep(20);
        Submission accepted =
                seedTerminalSubmission(user, problemEntity, language, Verdict.ACCEPTED, "ac-" + stamp);

        // Anchoring the contest start to the accepted submission makes "minute 5" exact
        // regardless of how long the setup took.
        Instant contestStart = accepted.getSubmittedAt().minus(5, ChronoUnit.MINUTES);
        updateContestWindow(admin, contestId, name, false, 0,
                contestStart, contestStart.plus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/contests/{id}/standings", contestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozen").value(false))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].username").value(username))
                .andExpect(jsonPath("$.rows[0].totalPoints").value(250))
                .andExpect(jsonPath("$.rows[0].penalty").value(15))
                .andExpect(jsonPath("$.rows[0].problems[0].solved").value(true))
                .andExpect(jsonPath("$.rows[0].problems[0].wrongAttempts").value(1))
                .andExpect(jsonPath("$.rows[0].problems[0].acceptedAtMinute").value(5));
    }

    @Test
    void registrationClosesAtStart() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        String admin = adminToken("adm" + stamp);
        String user = userToken("usr" + stamp);

        Instant now = Instant.now();
        long contestId = createContest(admin, "Started " + stamp,
                now.minus(1, ChronoUnit.MINUTES), now.plus(1, ChronoUnit.HOURS), false, null);

        mockMvc.perform(post("/api/v1/contests/{id}/register", contestId)
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Registration is closed for this contest"));
    }

    private ResultActions submit(String token, long problemId, long languageId) throws Exception {
        return mockMvc.perform(post("/api/v1/submissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "problemId", problemId, "languageId", languageId, "sourceCode", "class Main {}"))));
    }

    /**
     * Problems can only be created/attached while a contest is UPCOMING (PRD §7.8), so a
     * contest is authored in the future and then moved into its real window via the update
     * endpoint — the only way to exercise started/ended contests with inline problems.
     */
    private long createUpcomingContest(String admin, String name, boolean isPrivate,
                                       Integer freezeMinutes) throws Exception {
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        return createContest(admin, name, start, start.plus(1, ChronoUnit.HOURS), isPrivate, freezeMinutes);
    }

    private long createContest(String admin, String name, Instant start, Instant end,
                               boolean isPrivate, Integer freezeMinutes) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/contests")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contestBody(name, start, end,
                                isPrivate, freezeMinutes))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void updateContestWindow(String admin, long contestId, String name, boolean isPrivate,
                                     Integer freezeMinutes, Instant start, Instant end) throws Exception {
        mockMvc.perform(put("/api/v1/admin/contests/{id}", contestId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contestBody(name, start, end,
                                isPrivate, freezeMinutes))))
                .andExpect(status().isOk());
    }

    private static Map<String, Object> contestBody(String name, Instant start, Instant end,
                                                   boolean isPrivate, Integer freezeMinutes) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", "Description of " + name);
        body.put("startTime", start.toString());
        body.put("endTime", end.toString());
        body.put("isPrivate", isPrivate);
        if (freezeMinutes != null) {
            body.put("freezeMinutes", freezeMinutes);
        }
        return body;
    }

    private JsonNode createContestProblem(String admin, long contestId, String title,
                                          Integer points) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("statement", "Given two numbers, print their sum.");
        body.put("difficulty", "EASY");
        if (points != null) {
            body.put("points", points);
        }
        String response = mockMvc.perform(post("/api/v1/admin/contests/{id}/problems/new", contestId)
                        .header("Authorization", "Bearer " + admin)
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

    private void registerForContest(String token, long contestId) throws Exception {
        mockMvc.perform(post("/api/v1/contests/{id}/register", contestId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true));
    }

    private Submission seedTerminalSubmission(User user, Problem problem, Language language,
                                              Verdict verdict, String idempotencyKey) {
        Submission submission = new Submission(user, problem, language, "class Main {}", idempotencyKey);
        submission.transitionTo(SubmissionStatus.QUEUED);
        submission.transitionTo(SubmissionStatus.PICKED_UP);
        submission.transitionTo(SubmissionStatus.RUNNING);
        submission.transitionTo(SubmissionStatus.EVALUATING);
        submission.applyTerminalOutcome(SubmissionStatus.COMPLETED, verdict, 5, 512, null, null);
        return submissionRepository.saveAndFlush(submission);
    }

    private Language seedLanguage(String stamp) {
        return languageRepository.saveAndFlush(new Language("Java " + stamp, "Main.java",
                "javac Main.java", "java Main", "oj-java21", BigDecimal.ONE));
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
