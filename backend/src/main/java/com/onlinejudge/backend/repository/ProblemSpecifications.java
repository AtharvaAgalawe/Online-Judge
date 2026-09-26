package com.onlinejudge.backend.repository;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Tag;
import com.onlinejudge.common.enums.Difficulty;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

/**
 * Composable filters for the problem browse endpoint (PRD §7.1). The public list is
 * always restricted to published problems; unpublished ones are invisible to solvers.
 */
public final class ProblemSpecifications {

    private ProblemSpecifications() {
    }

    public static Specification<Problem> publishedOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("published"));
    }

    public static Specification<Problem> hasDifficulty(Difficulty difficulty) {
        return (root, query, cb) -> cb.equal(root.get("difficulty"), difficulty);
    }

    public static Specification<Problem> hasTag(String tag) {
        return (root, query, cb) -> {
            Join<Problem, Tag> tags = root.join("tags", JoinType.INNER);
            return cb.equal(cb.lower(tags.get("name")), tag.toLowerCase(Locale.ROOT));
        };
    }

    public static Specification<Problem> titleOrSlugContains(String search) {
        String pattern = "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(root.get("slug")), pattern, '\\'));
    }

    /** Escapes LIKE wildcards so user input cannot become a wildcard pattern. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
