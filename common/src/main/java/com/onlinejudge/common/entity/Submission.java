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
import jakarta.persistence.Version;

import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;
import com.onlinejudge.common.exception.IllegalStateTransitionException;

@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false, updatable = false)
    private Problem problem;

    @ManyToOne(optional = false)
    @JoinColumn(name = "language_id", nullable = false, updatable = false)
    private Language language;

    @Column(name = "source_code", nullable = false, columnDefinition = "text")
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Verdict verdict;

    @Column(name = "time_used_ms")
    private Integer timeUsedMs;

    @Column(name = "memory_used_kb")
    private Integer memoryUsedKb;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "failed_test_case_id")
    private TestCase failedTestCase;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "idempotency_key", unique = true, length = 100, updatable = false)
    private String idempotencyKey;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "judged_at", updatable = false)
    private Instant judgedAt;

    protected Submission() {
    }

    public Submission(User user, Problem problem, Language language, String sourceCode,
                      String idempotencyKey) {
        this.user = user;
        this.problem = problem;
        this.language = language;
        this.sourceCode = sourceCode;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void onSubmit() {
        this.submittedAt = Instant.now();
    }

    /**
     * The single gate for status changes (AGENTS.md §6): validates the transition against
     * the lifecycle graph in {@link SubmissionStatus} and throws on illegal transitions.
     */
    public void transitionTo(SubmissionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateTransitionException(id, status, target);
        }
        this.status = target;
    }

    /**
     * Records the terminal outcome. Invariants: the verdict is only settable together
     * with a terminal transition (PRD §17), and once judged, a submission never
     * receives a second terminal outcome — exactly-once judging (PRD §7.3).
     */
    public void applyTerminalOutcome(SubmissionStatus terminalStatus, Verdict verdict,
                                     Integer timeUsedMs, Integer memoryUsedKb,
                                     TestCase failedTestCase, String errorMessage) {
        if (!terminalStatus.isTerminal()) {
            throw new IllegalStateTransitionException(id, status, terminalStatus);
        }
        if (this.judgedAt != null) {
            throw new IllegalStateTransitionException(id, status, terminalStatus);
        }
        transitionTo(terminalStatus);
        this.verdict = verdict;
        this.timeUsedMs = timeUsedMs;
        this.memoryUsedKb = memoryUsedKb;
        this.failedTestCase = failedTestCase;
        this.errorMessage = errorMessage;
        this.judgedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Problem getProblem() {
        return problem;
    }

    public Language getLanguage() {
        return language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public SubmissionStatus getStatus() {
        return status;
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

    public TestCase getFailedTestCase() {
        return failedTestCase;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getVersion() {
        return version;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getJudgedAt() {
        return judgedAt;
    }
}
