package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.onlinejudge.backend.api.dto.StandingsProblemCell;
import com.onlinejudge.backend.api.dto.StandingsResponse;
import com.onlinejudge.backend.api.dto.StandingsRow;

/**
 * Standings cache fail-open behavior (PRD §24): a Redis outage or corrupt value degrades to
 * recomputation, never a failed request.
 */
@ExtendWith(MockitoExtension.class)
class StandingsCacheTest {

    private static final String KEY = "oj:standings:7";

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final JsonMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private StandingsCache cache;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        cache = new StandingsCache(redis, objectMapper);
    }

    @Test
    void returnsCachedStandings() throws JsonProcessingException {
        StandingsResponse stored = new StandingsResponse(true, Instant.parse("2026-01-01T10:00:00Z"),
                List.of(new StandingsRow(1, "alice", 100, 30,
                        List.of(new StandingsProblemCell(11L, true, 0, 30)))));
        when(valueOperations.get(KEY)).thenReturn(objectMapper.writeValueAsString(stored));

        Optional<StandingsResponse> result = cache.get(7L);

        assertThat(result).contains(stored);
    }

    @Test
    void getReturnsEmptyWhenKeyAbsent() {
        when(valueOperations.get(KEY)).thenReturn(null);

        assertThat(cache.get(7L)).isEmpty();
    }

    @Test
    void getFailsOpenWhenRedisIsDown() {
        when(valueOperations.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(cache.get(7L)).isEmpty();
    }

    @Test
    void getFailsOpenOnCorruptJson() {
        when(valueOperations.get(KEY)).thenReturn("{not-json");

        assertThat(cache.get(7L)).isEmpty();
    }

    @Test
    void putStoresJsonWithFifteenSecondTtl() throws JsonProcessingException {
        StandingsResponse standings = new StandingsResponse(false, null,
                List.of(new StandingsRow(1, "alice", 100, 30,
                        List.of(new StandingsProblemCell(11L, true, 0, 30)))));

        cache.put(7L, standings);

        verify(valueOperations).set(eq(KEY), eq(objectMapper.writeValueAsString(standings)),
                eq(Duration.ofSeconds(15)));
    }

    @Test
    void putFailsOpenWhenRedisIsDown() {
        doThrow(new RedisConnectionFailureException("down")).when(valueOperations)
                .set(eq(KEY), anyString(), any(Duration.class));

        cache.put(7L, new StandingsResponse(false, null, List.of()));
    }
}
