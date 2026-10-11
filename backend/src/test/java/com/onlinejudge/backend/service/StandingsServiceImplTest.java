package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.onlinejudge.backend.api.dto.StandingsProblemCell;
import com.onlinejudge.backend.api.dto.StandingsResponse;
import com.onlinejudge.backend.api.dto.StandingsRow;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ContestParticipantRepository;
import com.onlinejudge.backend.repository.ContestProblemRepository;
import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestParticipant;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Role;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.AppRole;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.Verdict;

/**
 * The standings math (PRD §7.8): first-AC points, penalty for wrong attempts before AC,
 * competition ranking, and the freeze snapshot. Submission instances are mocked (JPA-less
 * entities); contests/links/participants are real objects.
 */
@ExtendWith(MockitoExtension.class)
class StandingsServiceImplTest {

    private static final Instant NOW = Instant.now();

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestProblemRepository contestProblemRepository;

    @Mock
    private ContestParticipantRepository contestParticipantRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StandingsCache standingsCache;

    private StandingsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StandingsServiceImpl(contestRepository, contestProblemRepository,
                contestParticipantRepository, submissionRepository, userRepository, standingsCache);
    }

    @Test
    void solvedProblemEarnsPointsAndPenaltyFromFirstAc() {
        Contest contest = runningContest(1L, 0);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L);
        Instant start = contest.getStartTime();
        stubWindow(contest,
                List.of(new ContestProblem(contest, problem, 100, 0)),
                List.of(new ContestParticipant(contest, alice)),
                List.of(submission(Verdict.ACCEPTED, start.plusSeconds(1800), problem, alice)));

        StandingsResponse response = service.standings(1L, null);

        assertThat(response.frozen()).isFalse();
        assertThat(response.frozenAt()).isNull();
        assertThat(response.rows()).hasSize(1);
        StandingsRow row = response.rows().get(0);
        assertThat(row.rank()).isEqualTo(1);
        assertThat(row.username()).isEqualTo("alice");
        assertThat(row.totalPoints()).isEqualTo(100);
        assertThat(row.penalty()).isEqualTo(30);
        assertThat(row.problems()).hasSize(1);
        assertThat(row.problems().get(0).solved()).isTrue();
        assertThat(row.problems().get(0).wrongAttempts()).isZero();
        assertThat(row.problems().get(0).acceptedAtMinute()).isEqualTo(30);
    }

    @Test
    void wrongAttemptsBeforeAcAddTenMinutesEach() {
        Contest contest = runningContest(1L, 0);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L);
        Instant start = contest.getStartTime();
        Submission firstWrong = submission(Verdict.WRONG_ANSWER, start.plusSeconds(600), problem, alice);
        Submission compileError = submission(Verdict.COMPILATION_ERROR, start.plusSeconds(900), problem, alice);
        Submission secondWrong = submission(Verdict.WRONG_ANSWER, start.plusSeconds(1200), problem, alice);
        Submission accepted = submission(Verdict.ACCEPTED, start.plusSeconds(1800), problem, alice);
        stubWindow(contest,
                List.of(new ContestProblem(contest, problem, 100, 0)),
                List.of(new ContestParticipant(contest, alice)),
                List.of(secondWrong, accepted, firstWrong, compileError));

        StandingsResponse response = service.standings(1L, null);

        StandingsRow row = response.rows().get(0);
        assertThat(row.totalPoints()).isEqualTo(100);
        assertThat(row.penalty()).isEqualTo(60);
        assertThat(row.problems().get(0).wrongAttempts()).isEqualTo(3);
        assertThat(row.problems().get(0).acceptedAtMinute()).isEqualTo(30);
    }

    @Test
    void wrongAttemptsAfterAcAreIgnored() {
        Contest contest = runningContest(1L, 0);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L);
        Instant start = contest.getStartTime();
        Submission accepted = submission(Verdict.ACCEPTED, start.plusSeconds(1800), problem, alice);
        Submission laterWrong = submission(Verdict.WRONG_ANSWER, start.plusSeconds(2400), problem, alice);
        Submission laterError = submission(Verdict.COMPILATION_ERROR, start.plusSeconds(2700), problem, alice);
        stubWindow(contest,
                List.of(new ContestProblem(contest, problem, 100, 0)),
                List.of(new ContestParticipant(contest, alice)),
                List.of(accepted, laterWrong, laterError));

        StandingsResponse response = service.standings(1L, null);

        StandingsRow row = response.rows().get(0);
        assertThat(row.totalPoints()).isEqualTo(100);
        assertThat(row.penalty()).isEqualTo(30);
        assertThat(row.problems().get(0).wrongAttempts()).isZero();
    }

    @Test
    void systemErrorDoesNotCountAsWrongAttempt() {
        Contest contest = runningContest(1L, 0);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L);
        Instant start = contest.getStartTime();
        Submission systemError = submission(Verdict.SYSTEM_ERROR, start.plusSeconds(600), problem, alice);
        Submission wrong = submission(Verdict.WRONG_ANSWER, start.plusSeconds(900), problem, alice);
        Submission accepted = submission(Verdict.ACCEPTED, start.plusSeconds(1800), problem, alice);
        stubWindow(contest,
                List.of(new ContestProblem(contest, problem, 100, 0)),
                List.of(new ContestParticipant(contest, alice)),
                List.of(systemError, wrong, accepted));

        StandingsResponse response = service.standings(1L, null);

        StandingsRow row = response.rows().get(0);
        assertThat(row.totalPoints()).isEqualTo(100);
        assertThat(row.penalty()).isEqualTo(40);
        assertThat(row.problems().get(0).wrongAttempts()).isEqualTo(1);
    }

    @Test
    void unsolvedProblemShowsAttemptsButNoPoints() {
        Contest contest = runningContest(1L, 0);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L);
        Instant start = contest.getStartTime();
        Submission firstWrong = submission(Verdict.WRONG_ANSWER, start.plusSeconds(600), problem, alice);
        Submission systemError = submission(Verdict.SYSTEM_ERROR, start.plusSeconds(900), problem, alice);
        Submission secondWrong = submission(Verdict.TIME_LIMIT_EXCEEDED, start.plusSeconds(1200), problem, alice);
        stubWindow(contest,
                List.of(new ContestProblem(contest, problem, 100, 0)),
                List.of(new ContestParticipant(contest, alice)),
                List.of(firstWrong, systemError, secondWrong));

        StandingsResponse response = service.standings(1L, null);

        StandingsRow row = response.rows().get(0);
        assertThat(row.totalPoints()).isZero();
        assertThat(row.penalty()).isZero();
        StandingsProblemCell cell = row.problems().get(0);
        assertThat(cell.solved()).isFalse();
        assertThat(cell.wrongAttempts()).isEqualTo(2);
        assertThat(cell.acceptedAtMinute()).isNull();
    }

    @Test
    void rankingIsPointsDescPenaltyAscWithCompetitionRanking() {
        Contest contest = runningContest(1L, 0);
        User alice = user(5L, "alice", false);
        User bob = user(6L, "bob", false);
        User carol = user(7L, "carol", false);
        Problem first = problem(11L);
        Problem second = problem(12L);
        Instant start = contest.getStartTime();
        stubWindow(contest,
                List.of(new ContestProblem(contest, first, 100, 0),
                        new ContestProblem(contest, second, 50, 1)),
                List.of(new ContestParticipant(contest, alice),
                        new ContestParticipant(contest, bob),
                        new ContestParticipant(contest, carol)),
                List.of(submission(Verdict.ACCEPTED, start.plusSeconds(2400), first, alice),
                        submission(Verdict.ACCEPTED, start.plusSeconds(2400), first, bob),
                        submission(Verdict.ACCEPTED, start.plusSeconds(600), second, carol)));

        StandingsResponse response = service.standings(1L, null);

        assertThat(response.rows()).extracting(StandingsRow::username)
                .containsExactly("alice", "bob", "carol");
        assertThat(response.rows()).extracting(StandingsRow::rank)
                .containsExactly(1, 1, 3);
        assertThat(response.rows()).extracting(StandingsRow::totalPoints)
                .containsExactly(100, 100, 50);
        assertThat(response.rows()).extracting(StandingsRow::penalty)
                .containsExactly(40, 40, 10);
    }

    @Test
    void freezeUsesSnapshotCutoff() {
        Contest contest = runningContest(1L, 60);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L);
        Instant start = contest.getStartTime();
        Instant frozenAt = contest.getEndTime().minusSeconds(60 * 60L);
        Submission beforeFreeze = submission(Verdict.WRONG_ANSWER, start.plusSeconds(1200), problem, alice);
        Submission afterFreeze = submission(Verdict.ACCEPTED, start.plusSeconds(4800), problem, alice);
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(standingsCache.get(1L)).thenReturn(Optional.empty());
        when(contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(new ContestProblem(contest, problem, 100, 0)));
        when(contestParticipantRepository.findByContestId(1L))
                .thenReturn(List.of(new ContestParticipant(contest, alice)));
        when(submissionRepository.findForStandings(anyCollection(), anyCollection(),
                eq(start), eq(frozenAt))).thenAnswer(invocation -> {
                    Instant cutoff = invocation.getArgument(3);
                    return List.of(beforeFreeze, afterFreeze).stream()
                            .filter(submission -> submission.getSubmittedAt().isBefore(cutoff))
                            .toList();
                });

        StandingsResponse response = service.standings(1L, null);

        assertThat(response.frozen()).isTrue();
        assertThat(response.frozenAt()).isEqualTo(frozenAt);
        StandingsRow row = response.rows().get(0);
        assertThat(row.totalPoints()).isZero();
        StandingsProblemCell cell = row.problems().get(0);
        assertThat(cell.solved()).isFalse();
        assertThat(cell.wrongAttempts()).isEqualTo(1);
    }

    @Test
    void noFreezeAfterEnd() {
        Contest contest = new Contest("Ended Cup", "description", NOW.minusSeconds(7200), NOW.minusSeconds(60),
                false, 60, creator());
        ReflectionTestUtils.setField(contest, "id", 1L);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L);
        Submission accepted = submission(Verdict.ACCEPTED, contest.getStartTime().plusSeconds(1800), problem, alice);
        stubWindow(contest,
                List.of(new ContestProblem(contest, problem, 100, 0)),
                List.of(new ContestParticipant(contest, alice)),
                List.of(accepted));

        StandingsResponse response = service.standings(1L, null);

        assertThat(response.frozen()).isFalse();
        assertThat(response.frozenAt()).isNull();
        StandingsRow row = response.rows().get(0);
        assertThat(row.totalPoints()).isEqualTo(100);
        assertThat(row.penalty()).isEqualTo(30);
        assertThat(row.problems().get(0).solved()).isTrue();
    }

    @Test
    void privateContestRequiresAdmin() {
        Contest privateContest = new Contest("Invite Only", "description", NOW.minusSeconds(3600),
                NOW.plusSeconds(3600), true, 0, creator());
        ReflectionTestUtils.setField(privateContest, "id", 8L);
        User alice = user(5L, "alice", false);
        User admin = user(1L, "admin", true);
        Problem problem = problem(11L);
        when(contestRepository.findById(8L)).thenReturn(Optional.of(privateContest));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(standingsCache.get(8L)).thenReturn(Optional.empty());
        when(contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(8L))
                .thenReturn(List.of(new ContestProblem(privateContest, problem, 100, 0)));
        when(contestParticipantRepository.findByContestId(8L))
                .thenReturn(List.of(new ContestParticipant(privateContest, admin)));
        Submission adminAc = submission(Verdict.ACCEPTED,
                privateContest.getStartTime().plusSeconds(1800), problem, admin);
        when(submissionRepository.findForStandings(anyCollection(), anyCollection(), any(), any()))
                .thenReturn(List.of(adminAc));

        assertThatThrownBy(() -> service.standings(8L, "alice"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Contest not found: 8");

        StandingsResponse response = service.standings(8L, "admin");

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).username()).isEqualTo("admin");
        assertThat(response.rows().get(0).totalPoints()).isEqualTo(100);
    }

    @Test
    void cacheHitSkipsComputation() {
        Contest contest = runningContest(1L, 0);
        StandingsResponse cached = new StandingsResponse(false, null, List.of(new StandingsRow(1, "alice",
                100, 30, List.of(new StandingsProblemCell(11L, true, 0, 30)))));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(standingsCache.get(1L)).thenReturn(Optional.of(cached));

        StandingsResponse response = service.standings(1L, null);

        assertThat(response).isEqualTo(cached);
        verify(contestProblemRepository, never()).findByContestIdOrderByDisplayOrderAsc(any());
        verify(contestParticipantRepository, never()).findByContestId(any());
        verify(submissionRepository, never()).findForStandings(anyCollection(), anyCollection(), any(), any());
        verify(standingsCache, never()).put(any(), any());
    }

    @Test
    void cacheStoresComputedResult() {
        Contest contest = runningContest(1L, 0);
        User alice = user(5L, "alice", false);
        Problem problem = problem(11L);
        stubWindow(contest,
                List.of(new ContestProblem(contest, problem, 100, 0)),
                List.of(new ContestParticipant(contest, alice)),
                List.of(submission(Verdict.ACCEPTED, contest.getStartTime().plusSeconds(1800), problem, alice)));

        StandingsResponse response = service.standings(1L, null);

        assertThat(response.rows()).hasSize(1);
        verify(standingsCache).put(eq(1L), eq(response));
    }

    /** A running contest with the standard non-freezing window unless freezeMinutes is given. */
    private Contest runningContest(Long id, int freezeMinutes) {
        Contest contest = new Contest("Open Cup", "description", NOW.minusSeconds(3600), NOW.plusSeconds(3600),
                false, freezeMinutes, creator());
        ReflectionTestUtils.setField(contest, "id", id);
        return contest;
    }

    private void stubWindow(Contest contest, List<ContestProblem> links,
                            List<ContestParticipant> participants, List<Submission> submissions) {
        when(contestRepository.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(standingsCache.get(contest.getId())).thenReturn(Optional.empty());
        when(contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(contest.getId()))
                .thenReturn(links);
        when(contestParticipantRepository.findByContestId(contest.getId())).thenReturn(participants);
        when(submissionRepository.findForStandings(anyCollection(), anyCollection(),
                eq(contest.getStartTime()), eq(contest.getEndTime()))).thenReturn(submissions);
    }

    private User creator() {
        User creator = new User("creator", "creator@example.com", "hash");
        ReflectionTestUtils.setField(creator, "id", 100L);
        return creator;
    }

    private User user(Long id, String username, boolean admin) {
        User user = new User(username, username + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        user.grantRole(new Role(admin ? AppRole.ROLE_ADMIN.name() : AppRole.ROLE_USER.name()));
        return user;
    }

    private Problem problem(Long id) {
        Problem problem = new Problem("problem-" + id, "Problem " + id, "statement", Difficulty.EASY,
                1000, 65_536, null);
        ReflectionTestUtils.setField(problem, "id", id);
        return problem;
    }

    private Submission submission(Verdict verdict, Instant submittedAt, Problem problem, User user) {
        Submission submission = mock(Submission.class);
        lenient().when(submission.getVerdict()).thenReturn(verdict);
        lenient().when(submission.getSubmittedAt()).thenReturn(submittedAt);
        lenient().when(submission.getProblem()).thenReturn(problem);
        lenient().when(submission.getUser()).thenReturn(user);
        return submission;
    }
}
