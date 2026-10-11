package com.onlinejudge.backend.repository;

import java.time.Instant;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.onlinejudge.common.entity.Contest;

/** Contest list filters (search + derived status windows). */
public final class ContestSpecifications {

    private ContestSpecifications() {
    }

    public static Specification<Contest> nameContains(String search) {
        String pattern = "%" + search.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\');
    }

    public static Specification<Contest> isUpcoming(Instant now) {
        return (root, query, cb) -> cb.greaterThan(root.get("startTime"), now);
    }

    public static Specification<Contest> isRunning(Instant now) {
        return (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get("startTime"), now),
                cb.greaterThan(root.get("endTime"), now));
    }

    public static Specification<Contest> isEnded(Instant now) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("endTime"), now);
    }

    /** Public listing is always restricted to non-private contests. */
    public static Specification<Contest> publicOnly() {
        return (root, query, cb) -> cb.isFalse(root.get("isPrivate"));
    }
}
