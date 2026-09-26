package com.onlinejudge.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.onlinejudge.common.entity.ExecutionJob;

public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, Long> {

    Optional<ExecutionJob> findBySubmissionId(Long submissionId);
}
