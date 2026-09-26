package com.onlinejudge.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.onlinejudge.common.entity.Problem;

/**
 * Dynamic browse filtering (optional difficulty/tag/search per PRD §7.1) uses
 * Specifications instead of one JPQL query with nullable parameters — Postgres binding
 * of null enum/string parameters in `:param IS NULL` predicates is fragile, and
 * Specifications compose the optional filters without them.
 */
public interface ProblemRepository extends JpaRepository<Problem, Long>, JpaSpecificationExecutor<Problem> {

    Optional<Problem> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
