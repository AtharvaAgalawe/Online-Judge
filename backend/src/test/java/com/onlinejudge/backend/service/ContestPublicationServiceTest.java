package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import com.onlinejudge.backend.repository.ContestProblemRepository;
import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Difficulty;

@ExtendWith(MockitoExtension.class)
class ContestPublicationServiceTest {

    private static final Instant NOW = Instant.now();

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestProblemRepository contestProblemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private ProblemRepository problemRepository;

    private ContestPublicationService service;

    @BeforeEach
    void setUp() {
        service = new ContestPublicationService(contestRepository, contestProblemRepository,
                testCaseRepository, problemRepository);
    }

    @Test
    void publishesProblemsWithTestCasesAndMarksContest() {
        Contest contest = endedPublicContest(1L);
        Problem first = unpublishedProblem(11L);
        Problem second = unpublishedProblem(12L);
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(
                new ContestProblem(contest, first, 100, 0),
                new ContestProblem(contest, second, 100, 1)));
        when(testCaseRepository.countByProblemId(11L)).thenReturn(2L);
        when(testCaseRepository.countByProblemId(12L)).thenReturn(3L);

        service.publishContest(1L);

        assertThat(first.isPublished()).isTrue();
        assertThat(second.isPublished()).isTrue();
        verify(problemRepository).save(first);
        verify(problemRepository).save(second);
        assertThat(contest.getProblemsPublishedAt()).isNotNull();
        verify(contestRepository).save(contest);
    }

    @Test
    void skipsProblemsWithoutTestCasesButStillMarksContest() {
        Contest contest = endedPublicContest(2L);
        Problem withoutTestCases = unpublishedProblem(21L);
        when(contestRepository.findById(2L)).thenReturn(Optional.of(contest));
        when(contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(2L))
                .thenReturn(List.of(new ContestProblem(contest, withoutTestCases, 100, 0)));
        when(testCaseRepository.countByProblemId(21L)).thenReturn(0L);

        service.publishContest(2L);

        assertThat(withoutTestCases.isPublished()).isFalse();
        verify(problemRepository, never()).save(withoutTestCases);
        assertThat(contest.getProblemsPublishedAt()).isNotNull();
        verify(contestRepository).save(contest);
    }

    @Test
    void alreadyProcessedContestIsNoOp() {
        Contest contest = endedPublicContest(3L);
        contest.markProblemsPublished(NOW.minusSeconds(30));
        when(contestRepository.findById(3L)).thenReturn(Optional.of(contest));

        service.publishContest(3L);

        verifyNoInteractions(contestProblemRepository, testCaseRepository, problemRepository);
        verify(contestRepository, never()).save(contest);
    }

    @Test
    void alreadyPublishedProblemIsNotRepublished() {
        Contest contest = endedPublicContest(4L);
        Problem alreadyPublished = unpublishedProblem(31L);
        alreadyPublished.setPublished(true);
        when(contestRepository.findById(4L)).thenReturn(Optional.of(contest));
        when(contestProblemRepository.findByContestIdOrderByDisplayOrderAsc(4L))
                .thenReturn(List.of(new ContestProblem(contest, alreadyPublished, 100, 0)));

        service.publishContest(4L);

        assertThat(alreadyPublished.isPublished()).isTrue();
        verify(problemRepository, never()).save(alreadyPublished);
        verify(testCaseRepository, never()).countByProblemId(31L);
        assertThat(contest.getProblemsPublishedAt()).isNotNull();
        verify(contestRepository).save(contest);
    }

    private Contest endedPublicContest(Long id) {
        Contest contest = new Contest("Past Cup", "description", NOW.minusSeconds(7200),
                NOW.minusSeconds(60), false, 60,
                new User("creator" + id, "creator" + id + "@example.com", "hash"));
        ReflectionTestUtils.setField(contest, "id", id);
        return contest;
    }

    private Problem unpublishedProblem(Long id) {
        Problem problem = new Problem("problem-" + id, "Problem " + id, "statement", Difficulty.EASY,
                1000, 65_536, null);
        ReflectionTestUtils.setField(problem, "id", id);
        return problem;
    }
}
