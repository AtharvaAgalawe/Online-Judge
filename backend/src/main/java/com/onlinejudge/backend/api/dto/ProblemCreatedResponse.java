package com.onlinejudge.backend.api.dto;

/** Creation response (PRD §15): the generated id and slug. */
public record ProblemCreatedResponse(Long id, String slug) {
}
