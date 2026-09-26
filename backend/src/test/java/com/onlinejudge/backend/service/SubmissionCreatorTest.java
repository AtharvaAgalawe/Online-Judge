package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.onlinejudge.backend.config.SubmissionProperties;
import com.onlinejudge.backend.exception.BusinessRuleException;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ExecutionJobRepository;
import com.onlinejudge.backend.repository.LanguageRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.SubmissionStatus;

@ExtendWith(MockitoExtension.class)
class SubmissionCreatorTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ExecutionJobRepository executionJobRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private LanguageRepository languageRepository;

    @Mock
    private UserRepository userRepository;

    private SubmissionCreator creator;

    @BeforeEach
    void setUp() {
        creator = new SubmissionCreator(submissionRepository, executionJobRepository, problemRepository,
                languageRepository, userRepository,
                new SubmissionProperties(Duration.ofSeconds(3), Duration.ofHours(24), 65_536));
    }

    @Test
    void oversizedSourceIsRejectedBeforeAnyLookup() {
        String big = "a".repeat(65_537);

        assertThatThrownBy(() -> creator.create(1L, 1L, 1L, big, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds");
        verifyNoInteractions(problemRepository, languageRepository, submissionRepository, executionJobRepository);
    }

    @Test
    void unpublishedProblemIsTreatedAs404() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(unpublishedProblem()));

        assertThatThrownBy(() -> creator.create(1L, 1L, 2L, "code", null))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(submissionRepository);
    }

    @Test
    void disabledLanguageIsTreatedAs404() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(publishedProblem()));
        Language disabled = enabledLanguage();
        disabled.setEnabled(false);
        when(languageRepository.findById(2L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> creator.create(1L, 1L, 2L, "code", null))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(submissionRepository);
    }

    @Test
    void persistsSubmissionAndExecutionJobTogether() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(publishedProblem()));
        when(languageRepository.findById(2L)).thenReturn(Optional.of(enabledLanguage()));
        when(userRepository.getReferenceById(7L)).thenReturn(new User("alice", "a@x.com", "h"));
        when(submissionRepository.saveAndFlush(any(Submission.class))).thenAnswer(i -> i.getArgument(0));

        Submission submission = creator.create(7L, 1L, 2L, "int main(){}", "key-1");

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getSourceCode()).isEqualTo("int main(){}");
        assertThat(submission.getIdempotencyKey()).isEqualTo("key-1");

        ArgumentCaptor<ExecutionJob> job = ArgumentCaptor.forClass(ExecutionJob.class);
        verify(executionJobRepository).save(job.capture());
        assertThat(job.getValue().getSubmission()).isSameAs(submission);
    }

    private Problem publishedProblem() {
        Problem problem = new Problem("two-sum", "Two Sum", "statement", Difficulty.EASY, 1000, 65536, null);
        problem.setPublished(true);
        return problem;
    }

    private Problem unpublishedProblem() {
        return new Problem("draft", "Draft", "statement", Difficulty.EASY, 1000, 65536, null);
    }

    private Language enabledLanguage() {
        return new Language("Java 21", "Main.java", "javac Main.java", "java Main", "oj-java21", BigDecimal.ONE);
    }
}
