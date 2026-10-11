package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.onlinejudge.backend.api.dto.AttachContestProblemRequest;
import com.onlinejudge.backend.api.dto.CreateContestProblemRequest;
import com.onlinejudge.backend.api.dto.CreateContestRequest;
import com.onlinejudge.backend.api.dto.UpdateContestRequest;
import com.onlinejudge.backend.exception.BusinessRuleException;
import com.onlinejudge.backend.exception.DuplicateResourceException;
import com.onlinejudge.backend.repository.ContestParticipantRepository;
import com.onlinejudge.backend.repository.ContestProblemRepository;
import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Difficulty;

@ExtendWith(MockitoExtension.class)
class ContestAdminServiceImplTest {

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
    private UserRepository userRepository;

    private ContestAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContestAdminServiceImpl(contestRepository, contestProblemRepository,
                contestParticipantRepository, problemRepository, userRepository);
    }

    @Test
    void createRejectsEndBeforeStart() {
        assertThatThrownBy(() -> service.create(new CreateContestRequest("Contest", null, NOW,
                NOW.minusSeconds(1), false, null), "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Contest end time must be after start time");
    }

    @Test
    void updateRejectsAfterStart() {
        Contest started = contest(1L, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(started));

        assertThatThrownBy(() -> service.update(1L, new UpdateContestRequest("Renamed", null,
                NOW.minusSeconds(60), NOW.plusSeconds(3600), false, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Contest can no longer be edited after it has started");
    }

    @Test
    void deleteRejectsAfterStart() {
        Contest started = contest(1L, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(started));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Contest can no longer be deleted after it has started");
        verify(contestRepository, never()).delete(any(Contest.class));
    }

    @Test
    void attachRejectsPublishedProblem() {
        Contest upcoming = contest(1L, NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        Problem published = problem(7L, true);
        when(contestRepository.findById(1L)).thenReturn(Optional.of(upcoming));
        when(problemRepository.findById(7L)).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> service.attachProblem(1L, new AttachContestProblemRequest(7L, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only unpublished problems can be attached to a contest");
    }

    @Test
    void attachRejectsAlreadyAttachedProblem() {
        Contest upcoming = contest(1L, NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        Problem unpublished = problem(7L, false);
        when(contestRepository.findById(1L)).thenReturn(Optional.of(upcoming));
        when(problemRepository.findById(7L)).thenReturn(Optional.of(unpublished));
        when(contestProblemRepository.findByProblemId(7L))
                .thenReturn(Optional.of(new ContestProblem(upcoming, unpublished, 100, 0)));

        assertThatThrownBy(() -> service.attachProblem(1L, new AttachContestProblemRequest(7L, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void attachSucceedsAndSetsOrder() {
        Contest upcoming = contest(1L, NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        Problem first = problem(7L, false);
        Problem second = problem(8L, false);
        when(contestRepository.findById(1L)).thenReturn(Optional.of(upcoming));
        when(problemRepository.findById(7L)).thenReturn(Optional.of(first));
        when(problemRepository.findById(8L)).thenReturn(Optional.of(second));
        when(contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of())
                .thenReturn(List.of(new ContestProblem(upcoming, problem(9L, false), 50, 3)));

        service.attachProblem(1L, new AttachContestProblemRequest(7L, null));
        service.attachProblem(1L, new AttachContestProblemRequest(8L, 250));

        ArgumentCaptor<ContestProblem> saved = ArgumentCaptor.forClass(ContestProblem.class);
        verify(contestProblemRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getDisplayOrder()).isZero();
        assertThat(saved.getAllValues().get(0).getPoints()).isEqualTo(100);
        assertThat(saved.getAllValues().get(1).getDisplayOrder()).isEqualTo(4);
        assertThat(saved.getAllValues().get(1).getPoints()).isEqualTo(250);
    }

    @Test
    void detachRejectsAfterStart() {
        Contest started = contest(1L, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(started));

        assertThatThrownBy(() -> service.detachProblem(1L, 7L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Contest can no longer be edited after it has started");
        verify(contestProblemRepository, never()).delete(any(ContestProblem.class));
    }

    @Test
    void createProblemRejectsAfterStart() {
        Contest started = contest(1L, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(started));

        assertThatThrownBy(() -> service.createProblem(1L, new CreateContestProblemRequest("Title",
                "Statement", Difficulty.EASY, null, null, null), "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Contest can no longer be edited after it has started");
    }

    @Test
    void createProblemCreatesUnpublishedAndAttaches() {
        Contest upcoming = contest(1L, NOW.plusSeconds(3600), NOW.plusSeconds(7200));
        User admin = new User("admin", "admin@example.com", "hash");
        when(contestRepository.findById(1L)).thenReturn(Optional.of(upcoming));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(problemRepository.saveAndFlush(any(Problem.class))).thenAnswer(invocation -> {
            Problem saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        var response = service.createProblem(1L, new CreateContestProblemRequest("Fresh Problem",
                "Statement", Difficulty.EASY, null, null, 250), "admin");

        ArgumentCaptor<Problem> savedProblem = ArgumentCaptor.forClass(Problem.class);
        verify(problemRepository).saveAndFlush(savedProblem.capture());
        assertThat(savedProblem.getValue().isPublished()).isFalse();
        assertThat(savedProblem.getValue().getCreatedBy()).isSameAs(admin);
        assertThat(savedProblem.getValue().getSlug()).isEqualTo("fresh-problem");
        assertThat(response.problemId()).isEqualTo(42L);

        ArgumentCaptor<ContestProblem> savedLink = ArgumentCaptor.forClass(ContestProblem.class);
        verify(contestProblemRepository).save(savedLink.capture());
        assertThat(savedLink.getValue().getProblem()).isSameAs(savedProblem.getValue());
        assertThat(savedLink.getValue().getPoints()).isEqualTo(250);
    }

    private Contest contest(Long id, Instant start, Instant end) {
        Contest contest = new Contest("Contest " + id, "description", start, end, false, 60,
                new User("creator" + id, "creator" + id + "@example.com", "hash"));
        ReflectionTestUtils.setField(contest, "id", id);
        return contest;
    }

    private Problem problem(Long id, boolean published) {
        Problem problem = new Problem("problem-" + id, "Problem " + id, "statement", Difficulty.EASY,
                1000, 65_536, null);
        ReflectionTestUtils.setField(problem, "id", id);
        problem.setPublished(published);
        return problem;
    }
}
