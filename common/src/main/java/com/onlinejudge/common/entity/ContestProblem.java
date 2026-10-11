package com.onlinejudge.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "contest_problems")
public class ContestProblem {

    @EmbeddedId
    private ContestProblemId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("contestId")
    @JoinColumn(name = "contest_id")
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("problemId")
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Column(nullable = false)
    private int points = 100;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ContestProblem() {
    }

    public ContestProblem(Contest contest, Problem problem, int points, int displayOrder) {
        this.id = new ContestProblemId(contest.getId(), problem.getId());
        this.contest = contest;
        this.problem = problem;
        this.points = points;
        this.displayOrder = displayOrder;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public ContestProblemId getId() {
        return id;
    }

    public Contest getContest() {
        return contest;
    }

    public Problem getProblem() {
        return problem;
    }

    public int getPoints() {
        return points;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
