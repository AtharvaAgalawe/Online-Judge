package com.onlinejudge.backend.api.dto;

/** Refresh response (PRD §15). */
public record AccessTokenResponse(String accessToken, long expiresIn) {
}
