package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.onlinejudge.backend.repository.ContestRepository;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.User;

/**
 * The scheduled publish pass must isolate a failing contest so the remaining contests in the
 * same tick are still processed (PRD §7.8).
 */
@ExtendWith(MockitoExtension.class)
class ContestProblemPublisherTest {

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestPublicationService contestPublicationService;

    private ContestProblemPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ContestProblemPublisher(contestRepository, contestPublicationService);
    }

    @Test
    void oneContestFailingDoesNotStopTheRest() {
        Contest first = contest(1L);
        Contest second = contest(2L);
        when(contestRepository.findByIsPrivateFalseAndEndTimeLessThanEqualAndProblemsPublishedAtIsNull(
                any(Instant.class))).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("boom")).when(contestPublicationService).publishContest(1L);

        assertThatCode(() -> publisher.publishEndedContests()).doesNotThrowAnyException();

        verify(contestPublicationService).publishContest(1L);
        verify(contestPublicationService).publishContest(2L);
    }

    private Contest contest(Long id) {
        Instant now = Instant.now();
        Contest contest = new Contest("Cup " + id, "description", now.minusSeconds(7200),
                now.minusSeconds(60), false, 0, new User("creator" + id, "creator" + id + "@example.com", "hash"));
        ReflectionTestUtils.setField(contest, "id", id);
        return contest;
    }
}
