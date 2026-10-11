package com.onlinejudge.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.onlinejudge.backend.repository.LanguageRepository;
import com.onlinejudge.common.entity.Language;

/**
 * Proves the seeded language catalog (PRD §13/§35): every judgeable language is visible
 * through the public API, and the compiled ones carry the exact sandbox commands
 * {@code ContainerRunner} executes (the worker tests run the same strings in Docker).
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class LanguageCatalogIntegrationTest {

    private static final List<String> EXPECTED_NAMES = List.of(
            "C 17", "C++ 17", "Go 1.23", "Java 21", "JavaScript (Node 22)", "Python 3.12");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LanguageRepository languageRepository;

    @Test
    void publicApiListsEverySeededLanguageInAlphabeticalOrder() throws Exception {
        mockMvc.perform(get("/api/v1/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains(EXPECTED_NAMES.toArray())));
    }

    @Test
    void cSeedsPointAtTheGccImage() {
        Language c = language("C 17");
        assertThat(c.getSourceFilename()).isEqualTo("main.c");
        assertThat(c.getCompileCmd()).isEqualTo("gcc -O2 -std=c17 -o /box/out/main /box/src/main.c");
        assertThat(c.getRunCmd()).isEqualTo("./main");
        assertThat(c.getDockerImage()).isEqualTo("oj-gcc13");
        assertThat(c.getTimeLimitMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void cppSeedsPointAtTheGccImage() {
        Language cpp = language("C++ 17");
        assertThat(cpp.getSourceFilename()).isEqualTo("main.cpp");
        assertThat(cpp.getCompileCmd()).isEqualTo("g++ -O2 -std=c++17 -o /box/out/main /box/src/main.cpp");
        assertThat(cpp.getRunCmd()).isEqualTo("./main");
        assertThat(cpp.getDockerImage()).isEqualTo("oj-gcc13");
    }

    @Test
    void javascriptIsInterpretedAndRunsOnNode() {
        Language javascript = language("JavaScript (Node 22)");
        assertThat(javascript.getSourceFilename()).isEqualTo("main.js");
        assertThat(javascript.isCompiled()).isFalse();
        assertThat(javascript.getRunCmd()).isEqualTo("node main.js");
        assertThat(javascript.getDockerImage()).isEqualTo("oj-node22");
    }

    @Test
    void goCompilesWithAWritableBuildCache() {
        Language go = language("Go 1.23");
        assertThat(go.getSourceFilename()).isEqualTo("main.go");
        assertThat(go.getCompileCmd()).isEqualTo(
                "HOME=/tmp GOCACHE=/tmp/gocache GOPATH=/tmp/gopath"
                        + " go build -p=1 -o /box/out/main /box/src/main.go");
        assertThat(go.getRunCmd()).isEqualTo("./main");
        assertThat(go.getDockerImage()).isEqualTo("oj-go123");
    }

    private Language language(String name) {
        return languageRepository.findAll().stream()
                .filter(language -> language.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Language not seeded: " + name));
    }
}
