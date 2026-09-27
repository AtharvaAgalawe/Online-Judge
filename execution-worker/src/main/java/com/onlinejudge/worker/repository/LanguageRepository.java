package com.onlinejudge.worker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.onlinejudge.common.entity.Language;

public interface LanguageRepository extends JpaRepository<Language, Long> {
}
