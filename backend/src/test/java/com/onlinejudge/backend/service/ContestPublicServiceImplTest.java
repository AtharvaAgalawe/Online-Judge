package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.onlinejudge.backend.api.dto.ContestDetailResponse;
import com.onlinejudge.backend.api.dto.ContestSummaryResponse;
import com.onlinejudge.backend.api.dto.PagedResponse;
import com.onlinejudge.backend.api.dto.RegistrationResponse;
import com.onlinejudge.backend.exception.BusinessRuleException;
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
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.AppRole;
import com.onlinejudge.common.enums.ContestStatus;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.Verdict;

@ExtendWith(MockitoExtension.class)
class ContestPublicServiceImplTest {

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

    private ContestPublicServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContestPublicServiceImpl(contestRepository, contestProblemRepository,
                contestParticipantRepository, submissionRepository, userRepository);
    }

    @Test
    void browseMapsStatusAndCounts() {
        Contest running = contest(1L, "Spring Cup", NOW.minusSeconds(60), NOW.plusSeconds(3600), false);
        when(contestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(running)));
        when(contestParticipantRepository.countByContestId(1L)).thenReturn(3L);
        when(contestProblemRepository.countByContestId(1L)).thenReturn(5L);

        PagedResponse<ContestSummaryResponse> response = service.browse(ContestStatus.RUNNING, 0, 20);

        assertThat(response.content()).hasSize(1);
        ContestSummaryResponse summary = response.content().get(0);
        assertThat(summary.id()).isEqualTo(1L);
        assertThat(summary.status()).isEqualTo(ContestStatus.RUNNING);
        assertThat(summary.participantCount()).isEqualTo(3L);
        assertThat(summary.problemCount()).isEqualTo(5L);
        assertThat(response.totalElements()).isEqualTo(1L);
    }

    @Test
    void detailHidesPrivateContestFromNonAdmin() {
        Contest privateContest = contest(2L, "Invite Only", NOW.plusSeconds(3600), NOW.plusSeconds(7200), true);
        when(contestRepository.findById(2L)).thenReturn(Optional.of(privateContest));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(9L, "alice", false)));

        assertThatThrownBy(() -> service.detail(2L, "alice"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Contest not found: 2");
    }

    @Test
    void detailShowsPrivateContestToAdmin() {
        Contest privateContest = contest(2L, "Invite Only", NOW.plusSeconds(3600), NOW.plusSeconds(7200), true);
        when(contestRepository.findById(2L)).thenReturn(Optional.of(privateContest));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", true)));

        ContestDetailResponse response = service.detail(2L, "admin");

        assertThat(response.name()).isEqualTo("Invite Only");
        assertThat(response.status()).isEqualTo(ContestStatus.UPCOMING);
        assertThat(response.problemsRevealed()).isTrue();
        assertThat(response.problems()).isEmpty();
        assertThat(response.registrationOpen()).isFalse();
    }

    @Test
    void detailMarksSolvedByMe() {
        Contest running = contest(3L, "Open Cup", NOW.minusSeconds(60), NOW.plusSeconds(3600), false);
        Problem first = problem(11L, "two-sum");
        Problem second = problem(12L, "three-sum");
        User alice = user(5L, "alice", false);
        when(contestRepository.findById(3L)).thenReturn(Optional.of(running));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(contestParticipantRepository.existsByContestIdAndUserId(3L, 5L)).thenReturn(true);
        when(contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(3L)).thenReturn(List.of(
                new ContestProblem(running, first, 100, 0),
                new ContestProblem(running, second, 100, 1)));
        when(submissionRepository.findProblemIdsSolvedBy(eq(5L), anyList(), eq(Verdict.ACCEPTED)))
                .thenReturn(List.of(11L));

        ContestDetailResponse response = service.detail(3L, "alice");

        assertThat(response.registered()).isTrue();
        assertThat(response.problemsRevealed()).isTrue();
        assertThat(response.problems()).hasSize(2);
        assertThat(response.problems().get(0).problemId()).isEqualTo(11L);
        assertThat(response.problems().get(0).solvedByMe()).isTrue();
        assertThat(response.problems().get(1).problemId()).isEqualTo(12L);
        assertThat(response.problems().get(1).solvedByMe()).isFalse();
    }

    @Test
    void detailHidesProblemsBeforeStart() {
        Contest upcoming = contest(4L, "Soon", NOW.plusSeconds(3600), NOW.plusSeconds(7200), false);
        when(contestRepository.findById(4L)).thenReturn(Optional.of(upcoming));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(5L, "alice", false)));

        ContestDetailResponse response = service.detail(4L, "alice");

        assertThat(response.problemsRevealed()).isFalse();
        assertThat(response.problems()).isNull();
        assertThat(response.registrationOpen()).isTrue();
    }

    @Test
    void detailRevealsProblemsForAdminBeforeStart() {
        Contest upcoming = contest(4L, "Soon", NOW.plusSeconds(3600), NOW.plusSeconds(7200), false);
        Problem problem = problem(11L, "two-sum");
        when(contestRepository.findById(4L)).thenReturn(Optional.of(upcoming));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", true)));
        when(contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(4L))
                .thenReturn(List.of(new ContestProblem(upcoming, problem, 100, 0)));
        when(submissionRepository.findProblemIdsSolvedBy(eq(1L), anyList(), eq(Verdict.ACCEPTED)))
                .thenReturn(List.of());

        ContestDetailResponse response = service.detail(4L, "admin");

        assertThat(response.problemsRevealed()).isTrue();
        assertThat(response.problems()).hasSize(1);
        assertThat(response.problems().get(0).problemId()).isEqualTo(11L);
    }

    @Test
    void registerRejectsAfterStart() {
        Contest running = contest(5L, "Live", NOW.minusSeconds(60), NOW.plusSeconds(3600), false);
        when(contestRepository.findById(5L)).thenReturn(Optional.of(running));

        assertThatThrownBy(() -> service.register(5L, "alice"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Registration is closed for this contest");
        verify(contestParticipantRepository, never()).save(any(ContestParticipant.class));
    }

    @Test
    void registerIsIdempotent() {
        Contest upcoming = contest(6L, "Soon", NOW.plusSeconds(3600), NOW.plusSeconds(7200), false);
        when(contestRepository.findById(6L)).thenReturn(Optional.of(upcoming));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(7L, "alice", false)));
        when(contestParticipantRepository.existsByContestIdAndUserId(6L, 7L)).thenReturn(true);

        RegistrationResponse response = service.register(6L, "alice");

        assertThat(response.registered()).isTrue();
        verify(contestParticipantRepository, never()).save(any(ContestParticipant.class));
    }

    @Test
    void registerRejectsPrivateContest() {
        Contest privateContest = contest(8L, "Invite Only", NOW.plusSeconds(3600), NOW.plusSeconds(7200), true);
        when(contestRepository.findById(8L)).thenReturn(Optional.of(privateContest));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(9L, "alice", false)));

        assertThatThrownBy(() -> service.register(8L, "alice"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(contestParticipantRepository, never()).save(any(ContestParticipant.class));
    }

    @Test
    void unregisterRejectsAfterStart() {
        Contest running = contest(5L, "Live", NOW.minusSeconds(60), NOW.plusSeconds(3600), false);
        when(contestRepository.findById(5L)).thenReturn(Optional.of(running));

        assertThatThrownBy(() -> service.unregister(5L, "alice"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Registration is closed for this contest");
        verify(contestParticipantRepository, never()).delete(any(ContestParticipant.class));
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
