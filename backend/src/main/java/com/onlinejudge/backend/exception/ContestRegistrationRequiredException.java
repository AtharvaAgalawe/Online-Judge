package com.onlinejudge.backend.exception;

/** The caller must register for the contest before submitting to its problems (PRD §7.8). */
public class ContestRegistrationRequiredException extends RuntimeException {

    public ContestRegistrationRequiredException(String message) {
        super(message);
    }
}
