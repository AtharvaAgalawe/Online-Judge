package com.onlinejudge.common.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "contest_participants")
public class ContestParticipant {

    @EmbeddedId
    private ContestParticipantId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("contestId")
    @JoinColumn(name = "contest_id")
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    protected ContestParticipant() {
    }

    public ContestParticipant(Contest contest, User user) {
        this.id = new ContestParticipantId(contest.getId(), user.getId());
        this.contest = contest;
        this.user = user;
    }

    @PrePersist
    void onRegister() {
        this.registeredAt = Instant.now();
    }

    public ContestParticipantId getId() {
        return id;
    }

    public Contest getContest() {
        return contest;
    }

    public User getUser() {
        return user;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
