package com.onlinejudge.backend.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.enums.SubmissionStatus;

public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, Long> {

    Optional<ExecutionJob> findBySubmissionId(Long submissionId);

    @Query("select count(j) from ExecutionJob j where j.submission.status in :statuses")
    long countBySubmissionStatusIn(@Param("statuses") Collection<SubmissionStatus> statuses);

    @Query("""
            select count(j) from ExecutionJob j
            where j.leaseExpiresAt > :now and j.submission.status not in :terminal
            """)
    long countActiveLeases(@Param("now") Instant now, @Param("terminal") Collection<SubmissionStatus> terminal);

    @Query("""
            select count(j) from ExecutionJob j
            where j.leaseExpiresAt <= :now and j.submission.status not in :terminal
            """)
    long countStaleLeases(@Param("now") Instant now, @Param("terminal") Collection<SubmissionStatus> terminal);

    @Query("select count(j) from ExecutionJob j where j.retryCount > 0")
    long countRetried();

    @Query("select min(j.createdAt) from ExecutionJob j where j.submission.status in :statuses")
    Instant findOldestQueuedCreatedAt(@Param("statuses") Collection<SubmissionStatus> statuses);

    @Query("""
            select j from ExecutionJob j
            where j.leaseExpiresAt <= :now and j.submission.status not in :terminal
            order by j.leaseExpiresAt asc
            """)
    List<ExecutionJob> findStaleLeases(@Param("now") Instant now,
                                       @Param("terminal") Collection<SubmissionStatus> terminal,
                                       Pageable pageable);
}
