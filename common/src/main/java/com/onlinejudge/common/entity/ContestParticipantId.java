package com.onlinejudge.common.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ContestParticipantId implements Serializable {

    @Column(name = "contest_id")
    private Long contestId;

    @Column(name = "user_id")
    private Long userId;

    protected ContestParticipantId() {
    }

    public ContestParticipantId(Long contestId, Long userId) {
        this.contestId = contestId;
        this.userId = userId;
    }

    public Long getContestId() {
        return contestId;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContestParticipantId that)) {
            return false;
        }
        return Objects.equals(contestId, that.contestId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contestId, userId);
    }
}
