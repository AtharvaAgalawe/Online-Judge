package com.onlinejudge.worker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.onlinejudge.common.entity.ExecutionJob;

public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, Long> {

    Optional<ExecutionJob> findBySubmissionId(Long submissionId);

    /**
     * Lease acquisition is a single conditional UPDATE (PRD §12/§13) — the database row
     * is the only serialization point; no application-level lock exists. Native SQL is
     * used deliberately: the row-level atomicity of {@code UPDATE ... WHERE} plus the
     * database clock ({@code now()}) cannot be expressed in JPQL. Every acquisition
     * counts as one processing attempt ({@code retry_count}), so a submission that keeps
     * being redelivered is bounded by its {@code max_retries}.
     *
     * @return 1 when this worker won the lease, 0 when another worker holds it
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE execution_jobs
            SET locked_by = :workerId,
                locked_at = now(),
                lease_expires_at = now() + make_interval(secs => :leaseSeconds),
                retry_count = retry_count + 1
            WHERE submission_id = :submissionId
              AND (locked_by IS NULL OR lease_expires_at < now())
            """, nativeQuery = true)
    int tryAcquireLease(@Param("submissionId") long submissionId, @Param("workerId") String workerId,
                        @Param("leaseSeconds") double leaseSeconds);

    /** Releases this worker's lease after a failed attempt so the retry is not treated as a duplicate. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE execution_jobs
            SET locked_by = NULL, locked_at = NULL, lease_expires_at = NULL
            WHERE submission_id = :submissionId AND locked_by = :workerId
            """, nativeQuery = true)
    int releaseLease(@Param("submissionId") long submissionId, @Param("workerId") String workerId);
}
