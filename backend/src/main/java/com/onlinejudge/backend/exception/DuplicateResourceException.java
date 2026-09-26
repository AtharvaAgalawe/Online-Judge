package com.onlinejudge.backend.exception;

/** Unique-constraint conflict detected before or during persistence (→ 409). */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
