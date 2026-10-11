package com.onlinejudge.common.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ContestStatusTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant END = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void upcomingBeforeStart() {
        assertThat(ContestStatus.of(START, END, START.minusSeconds(1))).isEqualTo(ContestStatus.UPCOMING);
    }

    @Test
    void runningAtStartBoundary() {
        assertThat(ContestStatus.of(START, END, START)).isEqualTo(ContestStatus.RUNNING);
    }

    @Test
    void runningJustBeforeEnd() {
        assertThat(ContestStatus.of(START, END, END.minusMillis(1))).isEqualTo(ContestStatus.RUNNING);
    }

    @Test
    void endedAtEndBoundary() {
        assertThat(ContestStatus.of(START, END, END)).isEqualTo(ContestStatus.ENDED);
    }
}
