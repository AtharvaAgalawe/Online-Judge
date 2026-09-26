package com.onlinejudge.backend.exception;

/** Refresh token unknown, expired, revoked, or belonging to a disabled user (→ 401). */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid, expired or revoked");
    }
}
