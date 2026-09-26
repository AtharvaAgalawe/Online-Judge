package com.onlinejudge.backend.exception;

/** A violated business precondition (→ 400), e.g. publishing a problem that has no test cases. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
