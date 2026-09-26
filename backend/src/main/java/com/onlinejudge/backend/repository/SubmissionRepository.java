package com.onlinejudge.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.onlinejudge.common.entity.Submission;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Page<Submission> findByUserIdOrderBySubmittedAtDesc(Long userId, Pageable pageable);

    Page<Submission> findByProblemIdAndUserIdOrderBySubmittedAtDesc(Long problemId, Long userId, Pageable pageable);
}
