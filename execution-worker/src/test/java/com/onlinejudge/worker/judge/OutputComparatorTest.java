package com.onlinejudge.worker.judge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutputComparatorTest {

    @Test
    void exactMatchIsAccepted() {
        assertThat(OutputComparator.matches("5\n", "5\n")).isTrue();
    }

    @Test
    void trailingWhitespacePerLineIsIgnored() {
        assertThat(OutputComparator.matches("5   \n1 2\t\n", "5\n1 2\n")).isTrue();
    }

    @Test
    void trailingBlankLinesAreIgnored() {
        assertThat(OutputComparator.matches("5\n\n\n", "5\n")).isTrue();
        assertThat(OutputComparator.matches("5", "5\n\n")).isTrue();
    }

    @Test
    void windowsLineEndingsMatchUnix() {
        assertThat(OutputComparator.matches("5\r\n7\r\n", "5\n7\n")).isTrue();
    }

    @Test
    void internalSpacingIsSignificant() {
        assertThat(OutputComparator.matches("1  2\n", "1 2\n")).isFalse();
    }

    @Test
    void leadingBlankLinesAreSignificant() {
        assertThat(OutputComparator.matches("\n5\n", "5\n")).isFalse();
    }

    @Test
    void emptyOutputMatchesOnlyEmptyExpected() {
        assertThat(OutputComparator.matches("", "")).isTrue();
        assertThat(OutputComparator.matches("\n", "")).isTrue();
        assertThat(OutputComparator.matches("", "0\n")).isFalse();
    }
}
