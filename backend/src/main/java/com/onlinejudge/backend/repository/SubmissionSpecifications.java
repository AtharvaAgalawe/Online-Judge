package com.onlinejudge.backend.repository;

import org.springframework.data.jpa.domain.Specification;

import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

/** Composable filters for the admin submissions browser (PRD §7.7, §15). */
public final class SubmissionSpecifications {

    private SubmissionSpecifications() {
    }

    public static Specification<Submission> hasProblem(Long problemId) {
        return (root, query, cb) -> cb.equal(root.get("problem").get("id"), problemId);
    }

    public static Specification<Submission> hasUser(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Submission> hasStatus(SubmissionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Submission> hasVerdict(Verdict verdict) {
        return (root, query, cb) -> cb.equal(root.get("verdict"), verdict);
    }
}
