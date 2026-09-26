package com.onlinejudge.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.onlinejudge.common.entity.TestCase;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdOrderByDisplayOrderAsc(Long problemId);

    List<TestCase> findByProblemIdAndSampleTrueOrderByDisplayOrderAsc(Long problemId);

    long countByProblemId(Long problemId);
}
