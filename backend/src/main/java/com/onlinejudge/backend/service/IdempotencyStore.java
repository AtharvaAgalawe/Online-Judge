package com.onlinejudge.backend.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.onlinejudge.backend.config.SubmissionProperties;

/**
 * Redis-backed Idempotency-Key store (PRD §7.2, §24). Keys are scoped per user so one
 * user's key can never replay another user's submission.
 *
 * <p>Redis is an optimization here, not the correctness guarantee: the
 * {@code submissions.idempotency_key} unique constraint is the final authority. If Redis
 * is unavailable every operation degrades to that constraint instead of failing the
 * request (availability over the fast path), and the degraded path is handled explicitly
 * in the orchestration layer.
 */
@Component
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);
    static final String PENDING = "pending";

    private final StringRedisTemplate redis;
    private final SubmissionProperties properties;

    public IdempotencyStore(StringRedisTemplate redis, SubmissionProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** @return submission id of a completed request with this key, if any */
    public Optional<Long> findCompleted(Long userId, String idempotencyKey) {
        try {
            String value = redis.opsForValue().get(redisKey(userId, idempotencyKey));
            if (value == null || PENDING.equals(value)) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(value));
        } catch (DataAccessException e) {
            log.warn("Idempotency store unavailable during lookup for user {}; "
                    + "falling back to the database constraint: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Atomically claims the key for in-flight processing.
     *
     * @return false when the key is already claimed (in-flight or completed)
     */
    public boolean reserve(Long userId, String idempotencyKey) {
        try {
            Boolean reserved = redis.opsForValue()
                    .setIfAbsent(redisKey(userId, idempotencyKey), PENDING, properties.idempotencyTtl());
            return Boolean.TRUE.equals(reserved);
        } catch (DataAccessException e) {
            log.warn("Idempotency store unavailable during reserve for user {}; "
                    + "proceeding on the database constraint: {}", userId, e.getMessage());
            return true;
        }
    }

    public void complete(Long userId, String idempotencyKey, Long submissionId) {
        try {
            redis.opsForValue().set(redisKey(userId, idempotencyKey), submissionId.toString(),
                    properties.idempotencyTtl());
        } catch (DataAccessException e) {
            log.warn("Idempotency store unavailable during complete for user {}; the database "
                    + "constraint still prevents duplicate submissions: {}", userId, e.getMessage());
        }
    }

    /** Releases a reservation after a failed attempt so the client may retry the same key. */
    public void release(Long userId, String idempotencyKey) {
        try {
            redis.delete(redisKey(userId, idempotencyKey));
        } catch (DataAccessException e) {
            log.warn("Idempotency store unavailable during release for user {}: {}", userId, e.getMessage());
        }
    }

    private static String redisKey(Long userId, String idempotencyKey) {
        return "oj:idem:" + userId + ":" + idempotencyKey;
    }
}
