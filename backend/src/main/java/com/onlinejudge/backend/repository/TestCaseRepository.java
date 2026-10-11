package com.onlinejudge.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.onlinejudge.common.entity.TestCase;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdOrderByDisplayOrderAsc(Long problemId);

    List<TestCase> findByProblemIdAndSampleTrueOrderByDisplayOrderAsc(Long problemId);

    long countByProblemId(Long problemId);

    @Query("""
            select t.problem.id as problemId, count(t) as total
            from TestCase t
            where t.problem.id in :problemIds
            group by t.problem.id
            """)
    List<ProblemTestCaseCount> countByProblemIds(Collection<Long> problemIds);
}
