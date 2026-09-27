package com.onlinejudge.worker.judge;

import java.util.Arrays;

/**
 * Output comparison (PRD §20): exact match after whitespace normalization — trailing
 * whitespace per line is stripped and trailing blank lines are ignored; every other
 * difference, including internal spacing, is a wrong answer.
 */
public final class OutputComparator {

    private OutputComparator() {
    }

    public static boolean matches(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }

    static String normalize(String output) {
        String[] lines = output.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].stripTrailing();
        }
        int end = lines.length;
        while (end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        return String.join("\n", Arrays.copyOf(lines, end));
    }
}
