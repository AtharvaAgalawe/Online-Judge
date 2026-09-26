package com.onlinejudge.backend.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.LanguageResponse;
import com.onlinejudge.backend.service.LanguageService;

/** Public language catalog — deliberately unauthenticated (PRD §15). */
@RestController
@RequestMapping("/api/v1/languages")
public class LanguageController {

    private final LanguageService languageService;

    public LanguageController(LanguageService languageService) {
        this.languageService = languageService;
    }

    @GetMapping
    public List<LanguageResponse> list() {
        return languageService.listEnabled();
    }
}
