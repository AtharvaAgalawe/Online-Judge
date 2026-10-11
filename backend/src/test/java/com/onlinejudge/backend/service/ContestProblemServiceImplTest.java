package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.onlinejudge.backend.api.dto.ContestProblemDetailResponse;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ContestParticipantRepository;
import com.onlinejudge.backend.repository.ContestProblemRepository;
import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Role;
import com.onlinejudge.common.entity.TestCase;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.AppRole;
import com.onlinejudge.common.enums.Difficulty;

@ExtendWith(MockitoExtension.class)
class ContestProblemServiceImplTest {

    private static final Instant NOW = Instant.now();

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestProblemRepository contestProblemRepository;

    @Mock
    private ContestParticipantRepository contestParticipantRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private UserRepository userRepository;

    private ContestProblemServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContestProblemServiceImpl(contestRepository, contestProblemRepository,
                contestParticipantRepository, problemRepository, testCaseRepository, userRepository);
    }

    @Test
    void rejectsProblemNotInContest() {
        Contest running = contest(1L, "Spring Cup", NOW.minusSeconds(60), NOW.plusSeconds(3600), false);
        Contest other = contest(99L, "Other Cup", NOW.minusSeconds(60), NOW.plusSeconds(3600), false);
        Problem problem = problem(11L, "two-sum");
        when(contestRepository.findById(1L)).thenReturn(Optional.of(running));
        when(problemRepository.findBySlug("two-sum")).thenReturn(Optional.of(problem));
        when(contestProblemRepository.findByProblemId(11L))
                .thenReturn(Optional.of(new ContestProblem(other, problem, 100, 0)));

        assertThatThrownBy(() -> service.detail(1L, "two-sum", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Problem not found: two-sum");
    }

    @Test
    void rejectsBeforeStartForParticipant() {
        Contest upcoming = contest(2L, "Soon", NOW.plusSeconds(3600), NOW.plusSeconds(7200), false);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L, "two-sum");
        when(contestRepository.findById(2L)).thenReturn(Optional.of(upcoming));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(problemRepository.findBySlug("two-sum")).thenReturn(Optional.of(problem));
        when(contestProblemRepository.findByProblemId(11L))
                .thenReturn(Optional.of(new ContestProblem(upcoming, problem, 100, 0)));
        when(contestParticipantRepository.existsByContestIdAndUserId(2L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.detail(2L, "two-sum", "alice"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Problem not found: two-sum");
    }

    @Test
    void allowsRegisteredParticipantWhileRunning() {
        Contest running = contest(3L, "Open Cup", NOW.minusSeconds(60), NOW.plusSeconds(3600), false);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L, "two-sum");
        when(contestRepository.findById(3L)).thenReturn(Optional.of(running));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(problemRepository.findBySlug("two-sum")).thenReturn(Optional.of(problem));
        when(contestProblemRepository.findByProblemId(11L))
                .thenReturn(Optional.of(new ContestProblem(running, problem, 250, 0)));
        when(contestParticipantRepository.existsByContestIdAndUserId(3L, 5L)).thenReturn(true);
        when(testCaseRepository.findByProblemIdAndSampleTrueOrderByDisplayOrderAsc(11L))
                .thenReturn(List.of(new TestCase("1 2", "3", true, 0, 1),
                        new TestCase("2 2", "4", true, 1, 1)));

        ContestProblemDetailResponse response = service.detail(3L, "two-sum", "alice");

        assertThat(response.problemId()).isEqualTo(11L);
        assertThat(response.slug()).isEqualTo("two-sum");
        assertThat(response.title()).isEqualTo("Problem 11");
        assertThat(response.statement()).isEqualTo("statement");
        assertThat(response.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(response.timeLimitMs()).isEqualTo(1000);
        assertThat(response.memoryLimitKb()).isEqualTo(65_536);
        assertThat(response.points()).isEqualTo(250);
        assertThat(response.contestId()).isEqualTo(3L);
        assertThat(response.contestName()).isEqualTo("Open Cup");
        assertThat(response.sampleTestCases()).hasSize(2);
        assertThat(response.sampleTestCases().get(0).input()).isEqualTo("1 2");
        assertThat(response.sampleTestCases().get(0).expectedOutput()).isEqualTo("3");
        assertThat(response.sampleTestCases().get(0).order()).isEqualTo(0);
    }

    @Test
    void rejectsUnregisteredUserWhileRunning() {
        Contest running = contest(4L, "Open Cup", NOW.minusSeconds(60), NOW.plusSeconds(3600), false);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L, "two-sum");
        when(contestRepository.findById(4L)).thenReturn(Optional.of(running));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(problemRepository.findBySlug("two-sum")).thenReturn(Optional.of(problem));
        when(contestProblemRepository.findByProblemId(11L))
                .thenReturn(Optional.of(new ContestProblem(running, problem, 100, 0)));
        when(contestParticipantRepository.existsByContestIdAndUserId(4L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> service.detail(4L, "two-sum", "alice"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Problem not found: two-sum");
    }

    @Test
    void allowsAdminWhileRunning() {
        Contest running = contest(5L, "Open Cup", NOW.minusSeconds(60), NOW.plusSeconds(3600), false);
        User admin = user(1L, "admin", true);
        Problem problem = problem(11L, "two-sum");
        when(contestRepository.findById(5L)).thenReturn(Optional.of(running));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(problemRepository.findBySlug("two-sum")).thenReturn(Optional.of(problem));
        when(contestProblemRepository.findByProblemId(11L))
                .thenReturn(Optional.of(new ContestProblem(running, problem, 100, 0)));

        ContestProblemDetailResponse response = service.detail(5L, "two-sum", "admin");

        assertThat(response.problemId()).isEqualTo(11L);
        assertThat(response.points()).isEqualTo(100);
        assertThat(response.contestName()).isEqualTo("Open Cup");
        assertThat(response.sampleTestCases()).isEmpty();
    }

    @Test
    void allowsAfterEndWhenPublished() {
        Contest ended = contest(6L, "Past Cup", NOW.minusSeconds(7200), NOW.minusSeconds(60), false);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L, "two-sum");
        problem.setPublished(true);
        when(contestRepository.findById(6L)).thenReturn(Optional.of(ended));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(problemRepository.findBySlug("two-sum")).thenReturn(Optional.of(problem));
        when(contestProblemRepository.findByProblemId(11L))
                .thenReturn(Optional.of(new ContestProblem(ended, problem, 100, 0)));
        when(contestParticipantRepository.existsByContestIdAndUserId(6L, 5L)).thenReturn(false);

        ContestProblemDetailResponse response = service.detail(6L, "two-sum", "alice");

        assertThat(response.problemId()).isEqualTo(11L);
        assertThat(response.contestName()).isEqualTo("Past Cup");
        assertThat(response.sampleTestCases()).isEmpty();
    }

    @Test
    void rejectsAfterEndWhenNotPublished() {
        Contest ended = contest(7L, "Past Cup", NOW.minusSeconds(7200), NOW.minusSeconds(60), false);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L, "two-sum");
        when(contestRepository.findById(7L)).thenReturn(Optional.of(ended));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(problemRepository.findBySlug("two-sum")).thenReturn(Optional.of(problem));
        when(contestProblemRepository.findByProblemId(11L))
                .thenReturn(Optional.of(new ContestProblem(ended, problem, 100, 0)));
        when(contestParticipantRepository.existsByContestIdAndUserId(7L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> service.detail(7L, "two-sum", "alice"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Problem not found: two-sum");
    }

    @Test
    void rejectsNonAdminOnPrivateContest() {
        Contest privateContest = contest(8L, "Invite Only", NOW.minusSeconds(60), NOW.plusSeconds(3600), true);
        User alice = user(5L, "alice", false);
        when(contestRepository.findById(8L)).thenReturn(Optional.of(privateContest));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> service.detail(8L, "two-sum", "alice"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Problem not found: two-sum");
    }

    @Test
    void allowsAdminBeforeStart() {
        Contest upcoming = contest(9L, "Soon", NOW.plusSeconds(3600), NOW.plusSeconds(7200), false);
        User admin = user(1L, "admin", true);
        Problem problem = problem(11L, "two-sum");
        when(contestRepository.findById(9L)).thenReturn(Optional.of(upcoming));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(problemRepository.findBySlug("two-sum")).thenReturn(Optional.of(problem));
        when(contestProblemRepository.findByProblemId(11L))
                .thenReturn(Optional.of(new ContestProblem(upcoming, problem, 100, 0)));

        ContestProblemDetailResponse response = service.detail(9L, "two-sum", "admin");

        assertThat(response.problemId()).isEqualTo(11L);
        assertThat(response.points()).isEqualTo(100);
    }

    private Contest contest(Long id, String name, Instant start, Instant end, boolean isPrivate) {
        Contest contest = new Contest(name, "description", start, end, isPrivate, 60,
                new User("creator" + id, "creator" + id + "@example.com", "hash"));
        ReflectionTestUtils.setField(contest, "id", id);
        return contest;
    }

    private User user(Long id, String username, boolean admin) {
        User user = new User(username, username + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        user.grantRole(new Role(admin ? AppRole.ROLE_ADMIN.name() : AppRole.ROLE_USER.name()));
        return user;
    }

    private Problem problem(Long id, String slug) {
        Problem problem = new Problem(slug, "Problem " + id, "statement", Difficulty.EASY,
                1000, 65_536, null);
        ReflectionTestUtils.setField(problem, "id", id);
        return problem;
    }
}
