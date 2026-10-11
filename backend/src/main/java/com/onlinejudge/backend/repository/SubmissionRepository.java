package com.onlinejudge.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.enums.Verdict;

public interface SubmissionRepository extends JpaRepository<Submission, Long>, JpaSpecificationExecutor<Submission> {

    Optional<Submission> findByIdempotencyKeyAndUserId(String idempotencyKey, Long userId);

    Page<Submission> findByUserIdOrderBySubmittedAtDesc(Long userId, Pageable pageable);

    Page<Submission> findByProblemIdAndUserIdOrderBySubmittedAtDesc(Long problemId, Long userId, Pageable pageable);

    long countByProblemId(Long problemId);

    long countByProblemIdAndVerdict(Long problemId, Verdict verdict);

    @Query("""
            select s.problem.id as problemId, count(s) as total,
                   coalesce(sum(case when s.verdict = :accepted then 1 else 0 end), 0) as accepted
            from Submission s
            where s.problem.id in :problemIds and s.verdict is not null
            group by s.problem.id
            """)
    List<ProblemSubmissionStats> aggregateStatsByProblemIds(Collection<Long> problemIds, Verdict accepted);
}
