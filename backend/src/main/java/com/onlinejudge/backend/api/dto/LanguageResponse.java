package com.onlinejudge.backend.api.dto;

import com.onlinejudge.common.entity.Language;

/** Public language entry for the submission form (PRD §15). */
public record LanguageResponse(Long id, String name) {

    public static LanguageResponse from(Language language) {
        return new LanguageResponse(language.getId(), language.getName());
    }
}
