package com.onlinejudge.backend.exception;

/**
 * An Idempotency-Key is currently being processed by another request, or was used by a
 * different user (→ 409). Clients should retry the same key to obtain the original result.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
