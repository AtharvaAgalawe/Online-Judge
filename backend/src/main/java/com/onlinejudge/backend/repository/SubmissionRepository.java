package com.onlinejudge.backend.repository;

import java.time.Instant;
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

    /** Problem ids the user has solved (any ACCEPTED submission) — contest "solved" marks. */
    @Query("""
            select distinct s.problem.id from Submission s
            where s.user.id = :userId and s.problem.id in :problemIds and s.verdict = :verdict
            """)
    List<Long> findProblemIdsSolvedBy(Long userId, Collection<Long> problemIds, Verdict verdict);

    /** Terminal submissions for standings within a window (index: problem_id, submitted_at). */
    @Query("""
            select s from Submission s
            where s.problem.id in :problemIds
              and s.user.id in :userIds
              and s.submittedAt >= :start
              and s.submittedAt < :cutoff
              and s.verdict is not null
            """)
    List<Submission> findForStandings(Collection<Long> problemIds, Collection<Long> userIds,
                                      Instant start, Instant cutoff);
}
