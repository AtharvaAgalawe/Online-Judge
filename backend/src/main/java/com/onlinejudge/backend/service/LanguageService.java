package com.onlinejudge.backend.service;

import java.util.List;

import com.onlinejudge.backend.api.dto.LanguageResponse;

public interface LanguageService {

    List<LanguageResponse> listEnabled();
}
