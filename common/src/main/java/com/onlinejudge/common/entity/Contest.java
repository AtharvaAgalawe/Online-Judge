package com.onlinejudge.common.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "contests")
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "freeze_minutes", nullable = false)
    private int freezeMinutes = 60;

    @Column(name = "problems_published_at")
    private Instant problemsPublishedAt;

    protected Contest() {
    }

    public Contest(String name, String description, Instant startTime, Instant endTime,
                   boolean isPrivate, int freezeMinutes, User createdBy) {
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isPrivate = isPrivate;
        this.freezeMinutes = freezeMinutes;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** Metadata edits are guarded to before-start by the service layer (PRD §7.8). */
    public void updateMetadata(String name, String description, Instant startTime, Instant endTime,
                               boolean isPrivate, int freezeMinutes) {
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isPrivate = isPrivate;
        this.freezeMinutes = freezeMinutes;
    }

    /** Set by the auto-publish job; the marker makes publishing exactly-once per contest. */
    public void markProblemsPublished(Instant publishedAt) {
        this.problemsPublishedAt = publishedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public int getFreezeMinutes() {
        return freezeMinutes;
    }

    public Instant getProblemsPublishedAt() {
        return problemsPublishedAt;
    }
}
