package com.onlinejudge.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.ContestProblemId;

public interface ContestProblemRepository extends JpaRepository<ContestProblem, ContestProblemId> {

    List<ContestProblem> findByContestIdOrderByDisplayOrderAsc(Long contestId);

    /** A problem belongs to at most one contest (unique index) — used by submission validation. */
    Optional<ContestProblem> findByProblemId(Long problemId);

    long countByContestId(Long contestId);

    @Query("select cp.problem.id from ContestProblem cp")
    List<Long> findAllProblemIds();
}
