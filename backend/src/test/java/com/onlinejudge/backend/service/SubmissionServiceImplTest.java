package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.onlinejudge.backend.api.dto.CreateSubmissionRequest;
import com.onlinejudge.backend.api.dto.SubmissionAcceptedResponse;
import com.onlinejudge.backend.exception.BusinessRuleException;
import com.onlinejudge.backend.exception.IdempotencyConflictException;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.SubmissionStatus;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceImplTest {

    private static final long USER_ID = 7L;
    private static final String KEY = "client-key-1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionCreator submissionCreator;

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private SubmissionRateLimiter rateLimiter;

    private SubmissionServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("alice", "alice@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        service = new SubmissionServiceImpl(userRepository, submissionRepository, submissionCreator,
                idempotencyStore, rateLimiter);
    }

    @Test
    void createWithoutKeyUsesRateLimiterAndPersists() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(submissionCreator.create(eq(USER_ID), eq(1L), eq(2L), eq("code"), isNull(), anyString()))
                .thenReturn(submission(42L, user));

        SubmissionAcceptedResponse response = service.create("alice", request(), null);

        assertThat(response.submissionId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        verify(rateLimiter).acquire(USER_ID);
        verifyNoInteractions(idempotencyStore);
    }

    @Test
    void completedReplayReturnsOriginalWithoutConsumingRateLimit() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(idempotencyStore.findCompleted(USER_ID, KEY)).thenReturn(Optional.of(42L));
        when(submissionRepository.findById(42L)).thenReturn(Optional.of(submission(42L, user)));

        SubmissionAcceptedResponse response = service.create("alice", request(), KEY);

        assertThat(response.submissionId()).isEqualTo(42L);
        verifyNoInteractions(rateLimiter, submissionCreator);
    }

    @Test
    void replayOfAnotherUsersSubmissionConflicts() {
        User other = new User("mallory", "m@example.com", "h");
        ReflectionTestUtils.setField(other, "id", 99L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(idempotencyStore.findCompleted(USER_ID, KEY)).thenReturn(Optional.of(42L));
        when(submissionRepository.findById(42L)).thenReturn(Optional.of(submission(42L, other)));

        assertThatThrownBy(() -> service.create("alice", request(), KEY))
                .isInstanceOf(IdempotencyConflictException.class);
        verifyNoInteractions(submissionCreator);
    }

    @Test
    void inFlightRequestWithSameKeyConflicts() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(idempotencyStore.findCompleted(USER_ID, KEY)).thenReturn(Optional.empty());
        when(idempotencyStore.reserve(USER_ID, KEY)).thenReturn(false);

        assertThatThrownBy(() -> service.create("alice", request(), KEY))
                .isInstanceOf(IdempotencyConflictException.class);
        verifyNoInteractions(submissionCreator);
    }

    @Test
    void losingTheReservationRaceReplaysTheWinner() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(idempotencyStore.findCompleted(USER_ID, KEY))
                .thenReturn(Optional.empty(), Optional.of(42L));
        when(idempotencyStore.reserve(USER_ID, KEY)).thenReturn(false);
        when(submissionRepository.findById(42L)).thenReturn(Optional.of(submission(42L, user)));

        SubmissionAcceptedResponse response = service.create("alice", request(), KEY);

        assertThat(response.submissionId()).isEqualTo(42L);
        verifyNoInteractions(submissionCreator);
    }

    @Test
    void releasesReservationWhenCreationFails() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(idempotencyStore.findCompleted(USER_ID, KEY)).thenReturn(Optional.empty());
        when(idempotencyStore.reserve(USER_ID, KEY)).thenReturn(true);
        when(submissionCreator.create(eq(USER_ID), eq(1L), eq(2L), eq("code"), eq(KEY), anyString()))
                .thenThrow(new BusinessRuleException("too large"));

        assertThatThrownBy(() -> service.create("alice", request(), KEY))
                .isInstanceOf(BusinessRuleException.class);
        verify(idempotencyStore).release(USER_ID, KEY);
    }

    @Test
    void completesReservationAfterSuccess() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(idempotencyStore.findCompleted(USER_ID, KEY)).thenReturn(Optional.empty());
        when(idempotencyStore.reserve(USER_ID, KEY)).thenReturn(true);
        when(submissionCreator.create(eq(USER_ID), eq(1L), eq(2L), eq("code"), eq(KEY), anyString()))
                .thenReturn(submission(42L, user));

        service.create("alice", request(), KEY);

        verify(idempotencyStore).complete(USER_ID, KEY, 42L);
    }

    @Test
    void databaseConstraintRaceReplaysTheExistingSubmission() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(idempotencyStore.findCompleted(USER_ID, KEY)).thenReturn(Optional.empty());
        when(idempotencyStore.reserve(USER_ID, KEY)).thenReturn(true);
        when(submissionCreator.create(eq(USER_ID), eq(1L), eq(2L), eq("code"), eq(KEY), anyString()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(submissionRepository.findByIdempotencyKeyAndUserId(KEY, USER_ID))
                .thenReturn(Optional.of(submission(42L, user)));

        SubmissionAcceptedResponse response = service.create("alice", request(), KEY);

        assertThat(response.submissionId()).isEqualTo(42L);
        verify(idempotencyStore).release(USER_ID, KEY);
    }

    @Test
    void oversizedIdempotencyKeyIsRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> service.create("alice", request(), "k".repeat(101)))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(userRepository, rateLimiter, idempotencyStore, submissionCreator);
    }

    @Test
    void unknownUserIs404() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("ghost", request(), null))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(rateLimiter, submissionCreator);
    }

    private CreateSubmissionRequest request() {
        return new CreateSubmissionRequest(1L, 2L, "code");
    }

    private Submission submission(Long id, User owner) {
        Submission submission = new Submission(owner, null, null, "code", null);
        ReflectionTestUtils.setField(submission, "id", id);
        return submission;
    }
}

