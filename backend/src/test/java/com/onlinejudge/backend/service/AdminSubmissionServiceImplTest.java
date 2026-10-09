package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.SubmissionStatus;

@ExtendWith(MockitoExtension.class)
class AdminSubmissionServiceImplTest {

    @Mock
    private SubmissionRepository submissionRepository;

    private AdminSubmissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminSubmissionServiceImpl(submissionRepository);
    }

    @Test
    void listMapsSubmissionsToSummariesAcrossAllUsers() {
        Submission submission = new Submission(new User("bob", "b@x.com", "h"),
                problem(), new Language("Java 21", "Main.java", "javac", "java", "oj-java21", java.math.BigDecimal.ONE),
                "class Main {}", null);
        when(submissionRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(submission)));

        var page = service.list(null, null, SubmissionStatus.COMPLETED, null, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).problemSlug()).isEqualTo("slug");
    }

    private Problem problem() {
        return new Problem("slug", "Title", "statement", Difficulty.EASY, 1000, 65536, null);
    }
}
