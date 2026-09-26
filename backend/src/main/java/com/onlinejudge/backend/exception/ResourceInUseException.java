package com.onlinejudge.backend.exception;

/** The resource exists but is referenced by history and cannot be deleted (→ 409). */
public class ResourceInUseException extends RuntimeException {

    public ResourceInUseException(String message) {
        super(message);
    }
}
