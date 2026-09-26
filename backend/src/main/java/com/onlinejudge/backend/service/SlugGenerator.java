package com.onlinejudge.backend.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * URL slug generation for problems. Deterministic and collision-safe: a title that
 * already has a slug gets a numeric suffix rather than failing the admin's request.
 */
public final class SlugGenerator {

    private SlugGenerator() {
    }

    public static String slugify(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        return normalized.isEmpty() ? "problem" : normalized;
    }

    /**
     * @param base      slug candidate
     * @param slugTaken predicate that reports whether a slug is already in use
     */
    public static String unique(String base, Predicate<String> slugTaken) {
        if (!slugTaken.test(base)) {
            return base;
        }
        for (int suffix = 2; ; suffix++) {
            String candidate = base + "-" + suffix;
            if (!slugTaken.test(candidate)) {
                return candidate;
            }
        }
    }
}
