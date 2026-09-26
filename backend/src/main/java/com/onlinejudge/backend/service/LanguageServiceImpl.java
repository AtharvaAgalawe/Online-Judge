package com.onlinejudge.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.LanguageResponse;
import com.onlinejudge.backend.repository.LanguageRepository;

@Service
@Transactional(readOnly = true)
public class LanguageServiceImpl implements LanguageService {

    private final LanguageRepository languageRepository;

    public LanguageServiceImpl(LanguageRepository languageRepository) {
        this.languageRepository = languageRepository;
    }

    @Override
    public List<LanguageResponse> listEnabled() {
        return languageRepository.findByEnabledTrueOrderByNameAsc().stream()
                .map(LanguageResponse::from)
                .toList();
    }
}
