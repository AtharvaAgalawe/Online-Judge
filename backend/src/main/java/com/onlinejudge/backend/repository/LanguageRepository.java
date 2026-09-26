package com.onlinejudge.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.onlinejudge.common.entity.Language;

public interface LanguageRepository extends JpaRepository<Language, Long> {

    List<Language> findByEnabledTrueOrderByNameAsc();

    boolean existsByIdAndEnabledTrue(Long id);
}
