package com.onlinejudge.backend.service;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.backend.api.dto.StandingsResponse;

/**
 * Cache-aside for standings (PRD §24): 15s TTL, fail-open — a Redis outage degrades to
 * recomputation, never a failed request (same posture as IdempotencyStore). Staleness is
 * bounded by the TTL; there is no explicit invalidation.
 */
@Component
public class StandingsCache {

    private static final Logger log = LoggerFactory.getLogger(StandingsCache.class);
    private static final Duration TTL = Duration.ofSeconds(15);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public StandingsCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<StandingsResponse> get(Long contestId) {
        try {
            String value = redis.opsForValue().get(key(contestId));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, StandingsResponse.class));
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Standings cache unavailable for contest {}: {}", contestId, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(Long contestId, StandingsResponse standings) {
        try {
            redis.opsForValue().set(key(contestId), objectMapper.writeValueAsString(standings), TTL);
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Standings cache unavailable for contest {}: {}", contestId, e.getMessage());
        }
    }

    private static String key(Long contestId) {
        return "oj:standings:" + contestId;
    }
}
