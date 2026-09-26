package com.onlinejudge.common.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Lease row backing exactly-once judging (PRD §17). The conditional lease UPDATE lives
 * in the execution-worker's repository; the backend only creates the row alongside the
 * submission (PRD §13 transaction boundary).
 */
@Entity
@Table(name = "execution_jobs")
public class ExecutionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private Submission submission;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExecutionJob() {
    }

    public ExecutionJob(Submission submission) {
        this.submission = submission;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Submission getSubmission() {
        return submission;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean hasActiveLease() {
        return lockedBy != null && leaseExpiresAt != null && leaseExpiresAt.isAfter(Instant.now());
    }

    public boolean retriesExhausted() {
        return retryCount >= maxRetries;
    }
}
