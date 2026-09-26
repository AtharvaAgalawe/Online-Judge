package com.onlinejudge.backend.exception;

/** Requested resource does not exist or must not be revealed as existing (→ 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
