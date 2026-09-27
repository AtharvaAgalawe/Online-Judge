package com.onlinejudge.worker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.onlinejudge.common.entity.Problem;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
}
