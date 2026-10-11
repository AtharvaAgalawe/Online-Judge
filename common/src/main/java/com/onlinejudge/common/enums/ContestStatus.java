package com.onlinejudge.common.enums;

import java.time.Instant;

/** Contest lifecycle derived from its time window — never stored (PRD §7.8). */
public enum ContestStatus {
    UPCOMING,
    RUNNING,
    ENDED;

    public static ContestStatus of(Instant start, Instant end, Instant now) {
        if (now.isBefore(start)) {
            return UPCOMING;
        }
        return now.isBefore(end) ? RUNNING : ENDED;
    }
}
