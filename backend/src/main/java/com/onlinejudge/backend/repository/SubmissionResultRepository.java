package com.onlinejudge.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.onlinejudge.common.entity.SubmissionResult;

public interface SubmissionResultRepository extends JpaRepository<SubmissionResult, Long> {

    List<SubmissionResult> findBySubmissionIdOrderByTestCaseDisplayOrderAsc(Long submissionId);
}
