package com.onlinejudge.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestParticipant;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Difficulty;

/**
 * Proves the V6 contest schema against a real Postgres: the one-contest-per-problem
 * unique index fires, contest deletion cascades to links and participants without
 * touching the problem, and the auto-publish job query selects exactly the public,
 * ended, not-yet-processed contests. Docker-backed (Testcontainers).
 */
@Tag("docker")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ContestRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private ContestProblemRepository contestProblemRepository;

    @Autowired
    private ContestParticipantRepository contestParticipantRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void uniqueProblemAcrossContests() {
        String stamp = String.valueOf(System.nanoTime());
        Instant now = Instant.now();
        Contest contestA = saveContest("Contest A " + stamp, false,
                now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS));
        Contest contestB = saveContest("Contest B " + stamp, false,
                now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS));
        Problem problem = saveProblem("unique-problem-" + stamp);

        contestProblemRepository.saveAndFlush(new ContestProblem(contestA, problem, 100, 0));

        assertThatThrownBy(() -> contestProblemRepository.saveAndFlush(
                        new ContestProblem(contestB, problem, 100, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingContestCascadesLinksAndParticipants() {
        String stamp = String.valueOf(System.nanoTime());
        Instant now = Instant.now();
        Contest contest = saveContest("Cascade " + stamp, false,
                now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS));
        Problem problem = saveProblem("cascade-problem-" + stamp);
        User user = userRepository.saveAndFlush(
                new User("u" + stamp, "u" + stamp + "@example.com", "hash-not-a-real-secret"));

        contestProblemRepository.saveAndFlush(new ContestProblem(contest, problem, 100, 0));
        contestParticipantRepository.saveAndFlush(new ContestParticipant(contest, user));

        contestRepository.delete(contest);
        contestRepository.flush();

        assertThat(contestProblemRepository.findByProblemId(problem.getId())).isEmpty();
        assertThat(contestParticipantRepository.existsByContestIdAndUserId(contest.getId(), user.getId()))
                .isFalse();
        assertThat(problemRepository.findById(problem.getId())).isPresent();
    }

    @Test
    void publishJobQuerySelectsOnlyPublicEndedUnprocessed() {
        String stamp = String.valueOf(System.nanoTime());
        Instant now = Instant.now();
        Instant past = now.minus(2, ChronoUnit.HOURS);
        Instant future = now.plus(2, ChronoUnit.HOURS);

        Contest selected = saveContest("Public ended " + stamp, false, past, past.plus(1, ChronoUnit.HOURS));

        Contest alreadyPublished = saveContest("Public ended published " + stamp, false, past,
                past.plus(1, ChronoUnit.HOURS));
        alreadyPublished.markProblemsPublished(now);
        contestRepository.saveAndFlush(alreadyPublished);

        saveContest("Private ended " + stamp, true, past, past.plus(1, ChronoUnit.HOURS));
        saveContest("Public future " + stamp, false, future, future.plus(1, ChronoUnit.HOURS));

        assertThat(contestRepository.findByIsPrivateFalseAndEndTimeLessThanEqualAndProblemsPublishedAtIsNull(now))
                .extracting(Contest::getId)
                .containsExactly(selected.getId());
    }

    private Contest saveContest(String name, boolean isPrivate, Instant startTime, Instant endTime) {
        return contestRepository.saveAndFlush(new Contest(name, "description", startTime, endTime,
                isPrivate, 60, null));
    }

    private Problem saveProblem(String slug) {
        return problemRepository.saveAndFlush(new Problem(slug, "Title " + slug, "statement",
                Difficulty.EASY, 2000, 262144, null));
    }
}
