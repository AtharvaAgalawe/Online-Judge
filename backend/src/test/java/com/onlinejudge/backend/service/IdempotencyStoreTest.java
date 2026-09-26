package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.onlinejudge.backend.config.SubmissionProperties;

@ExtendWith(MockitoExtension.class)
class IdempotencyStoreTest {

    private static final String KEY = "oj:idem:7:abc-123";

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyStore store;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        store = new IdempotencyStore(redis, new SubmissionProperties(Duration.ofSeconds(3), Duration.ofHours(24),
                65_536));
    }

    @Test
    void pendingReservationIsNotACompletedSubmission() {
        when(valueOperations.get(KEY)).thenReturn(IdempotencyStore.PENDING);

        assertThat(store.findCompleted(7L, "abc-123")).isEmpty();
    }

    @Test
    void completedSubmissionIsReturned() {
        when(valueOperations.get(KEY)).thenReturn("42");

        assertThat(store.findCompleted(7L, "abc-123")).contains(42L);
    }

    @Test
    void lookupFailsOpenWhenRedisIsDown() {
        when(valueOperations.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(store.findCompleted(7L, "abc-123")).isEmpty();
    }

    @Test
    void reserveUsesAtomicSetIfAbsentWithTtl() {
        when(valueOperations.setIfAbsent(eq(KEY), eq(IdempotencyStore.PENDING), any(Duration.class)))
                .thenReturn(true);

        assertThat(store.reserve(7L, "abc-123")).isTrue();
    }

    @Test
    void reserveReturnsFalseWhenKeyAlreadyClaimed() {
        when(valueOperations.setIfAbsent(eq(KEY), eq(IdempotencyStore.PENDING), any(Duration.class)))
                .thenReturn(false);

        assertThat(store.reserve(7L, "abc-123")).isFalse();
    }

    @Test
    void reserveFailsOpenToTheDatabaseConstraintWhenRedisIsDown() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(store.reserve(7L, "abc-123")).isTrue();
    }

    @Test
    void completeStoresSubmissionIdWithTtl() {
        store.complete(7L, "abc-123", 42L);

        verify(valueOperations).set(eq(KEY), eq("42"), any(Duration.class));
    }

    @Test
    void releaseDeletesTheKey() {
        store.release(7L, "abc-123");

        verify(redis).delete(KEY);
    }
}
