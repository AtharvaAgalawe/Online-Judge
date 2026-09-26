package com.onlinejudge.backend.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.onlinejudge.backend.config.SubmissionProperties;
import com.onlinejudge.backend.exception.RateLimitExceededException;

/**
 * Per-user submission rate limit, fixed window over Redis (PRD §24/§26: 1 per 3s).
 *
 * <p>Fails open when Redis is unavailable: rate limiting is abuse mitigation, not
 * correctness, and the PRD requires the API to stay responsive (NFR: availability).
 */
@Component
public class SubmissionRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SubmissionRateLimiter.class);

    private final StringRedisTemplate redis;
    private final SubmissionProperties properties;

    public SubmissionRateLimiter(StringRedisTemplate redis, SubmissionProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void acquire(Long userId) {
        long windowSeconds = Math.max(1, properties.rateLimitWindow().toSeconds());
        long window = Instant.now().getEpochSecond() / windowSeconds;
        String key = "oj:rate:submit:" + userId + ":" + window;

        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
        } catch (DataAccessException e) {
            log.warn("Rate limiter unavailable for user {}; allowing submission: {}", userId, e.getMessage());
            return;
        }

        if (count != null && count > 1L) {
            throw new RateLimitExceededException(windowSeconds);
        }
    }
}
