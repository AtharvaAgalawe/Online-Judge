package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

    @Test
    void slugifiesTitles() {
        assertThat(SlugGenerator.slugify("Two Sum")).isEqualTo("two-sum");
        assertThat(SlugGenerator.slugify("A+B Problem (Easy)")).isEqualTo("a-b-problem-easy");
        assertThat(SlugGenerator.slugify("  Spaces  &  Symbols!! ")).isEqualTo("spaces-symbols");
        assertThat(SlugGenerator.slugify("caf\u00e9 cr\u00e8me")).isEqualTo("cafe-creme");
    }

    @Test
    void fallsBackWhenTitleHasNoUsableCharacters() {
        assertThat(SlugGenerator.slugify("!!!")).isEqualTo("problem");
        assertThat(SlugGenerator.slugify("")).isEqualTo("problem");
    }

    @Test
    void appendsSuffixOnCollision() {
        Set<String> taken = Set.of("two-sum", "two-sum-2", "two-sum-3");

        assertThat(SlugGenerator.unique("two-sum", taken::contains)).isEqualTo("two-sum-4");
        assertThat(SlugGenerator.unique("fresh-title", taken::contains)).isEqualTo("fresh-title");
    }
}
