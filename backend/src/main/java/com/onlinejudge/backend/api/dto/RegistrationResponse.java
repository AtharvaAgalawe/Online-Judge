package com.onlinejudge.backend.api.dto;

/** Registration outcome; {@code registered} is always true on 200 (idempotent register). */
public record RegistrationResponse(boolean registered) {
}
