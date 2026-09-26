package com.onlinejudge.backend.api.dto;

/** Login response (PRD §15). {@code expiresIn} is the access-token lifetime in seconds. */
public record AuthTokensResponse(String accessToken, String refreshToken, long expiresIn) {
}
