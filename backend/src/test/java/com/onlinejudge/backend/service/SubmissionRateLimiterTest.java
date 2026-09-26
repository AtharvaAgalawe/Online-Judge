package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.onlinejudge.backend.exception.RateLimitExceededException;

@ExtendWith(MockitoExtension.class)
class SubmissionRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SubmissionRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        SubmissionProperties properties = new SubmissionProperties(Duration.ofSeconds(3), Duration.ofHours(24),
                65_536);
        rateLimiter = new SubmissionRateLimiter(redis, properties);
    }

    @Test
    void firstSubmissionInWindowIsAllowedAndWindowGetsTtl() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        rateLimiter.acquire(7L);

        verify(redis).expire(anyString(), any(Duration.class));
    }

    @Test
    void secondSubmissionInWindowIsRateLimitedWithRetryAfter() {
        when(valueOperations.increment(anyString())).thenReturn(2L);

        assertThatThrownBy(() -> rateLimiter.acquire(7L))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(3L));
    }

    @Test
    void redisFailureFailsOpen() {
        when(valueOperations.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        rateLimiter.acquire(7L);
    }
}
