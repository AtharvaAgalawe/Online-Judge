package com.onlinejudge.worker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.onlinejudge.common.entity.SubmissionResult;

public interface SubmissionResultRepository extends JpaRepository<SubmissionResult, Long> {
}
