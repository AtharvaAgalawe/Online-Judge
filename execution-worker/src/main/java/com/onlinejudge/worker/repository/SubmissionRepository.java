package com.onlinejudge.worker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.onlinejudge.common.entity.Submission;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
}
