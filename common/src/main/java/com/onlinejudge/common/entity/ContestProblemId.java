package com.onlinejudge.common.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ContestProblemId implements Serializable {

    @Column(name = "contest_id")
    private Long contestId;

    @Column(name = "problem_id")
    private Long problemId;

    protected ContestProblemId() {
    }

    public ContestProblemId(Long contestId, Long problemId) {
        this.contestId = contestId;
        this.problemId = problemId;
    }

    public Long getContestId() {
        return contestId;
    }

    public Long getProblemId() {
        return problemId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContestProblemId that)) {
            return false;
        }
        return Objects.equals(contestId, that.contestId) && Objects.equals(problemId, that.problemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contestId, problemId);
    }
}
