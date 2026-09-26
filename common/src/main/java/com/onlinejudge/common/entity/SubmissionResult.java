package com.onlinejudge.common.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.onlinejudge.common.enums.Verdict;

/** Verdict of one test case within one submission (PRD §13). */
@Entity
@Table(name = "submission_results",
        uniqueConstraints = @UniqueConstraint(name = "uq_submission_results_submission_test",
                columnNames = {"submission_id", "test_case_id"}))
public class SubmissionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Verdict verdict;

    @Column(name = "time_used_ms")
    private Integer timeUsedMs;

    @Column(name = "memory_used_kb")
    private Integer memoryUsedKb;

    @Column(name = "stdout_snippet", length = 2000)
    private String stdoutSnippet;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SubmissionResult() {
    }

    public SubmissionResult(Submission submission, TestCase testCase, Verdict verdict,
                            Integer timeUsedMs, Integer memoryUsedKb, String stdoutSnippet) {
        this.submission = submission;
        this.testCase = testCase;
        this.verdict = verdict;
        this.timeUsedMs = timeUsedMs;
        this.memoryUsedKb = memoryUsedKb;
        this.stdoutSnippet = stdoutSnippet;
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

    public TestCase getTestCase() {
        return testCase;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public Integer getTimeUsedMs() {
        return timeUsedMs;
    }

    public Integer getMemoryUsedKb() {
        return memoryUsedKb;
    }

    public String getStdoutSnippet() {
        return stdoutSnippet;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
