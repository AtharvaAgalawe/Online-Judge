package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.onlinejudge.backend.config.SubmissionProperties;
import com.onlinejudge.backend.exception.BusinessRuleException;
import com.onlinejudge.backend.exception.ContestRegistrationRequiredException;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.ContestParticipantRepository;
import com.onlinejudge.backend.repository.ContestProblemRepository;
import com.onlinejudge.backend.repository.ExecutionJobRepository;
import com.onlinejudge.backend.repository.LanguageRepository;
import com.onlinejudge.backend.repository.ProblemRepository;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.TestCaseRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.entity.Contest;
import com.onlinejudge.common.entity.ContestProblem;
import com.onlinejudge.common.entity.ExecutionJob;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Role;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.TestCase;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.AppRole;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.SubmissionStatus;

@ExtendWith(MockitoExtension.class)
class SubmissionCreatorTest {

    private static final String CORRELATION_ID = "cid-1";

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

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private ContestProblemRepository contestProblemRepository;

    @Mock
    private ContestParticipantRepository contestParticipantRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SubmissionCreator creator;

    @BeforeEach
    void setUp() {
        creator = new SubmissionCreator(submissionRepository, executionJobRepository, problemRepository,
                languageRepository, userRepository, testCaseRepository, contestProblemRepository,
                contestParticipantRepository, eventPublisher,
                new SubmissionProperties(Duration.ofSeconds(3), Duration.ofHours(24), 65_536));
    }

    @Test
    void oversizedSourceIsRejectedBeforeAnyLookup() {
        String big = "a".repeat(65_537);

        assertThatThrownBy(() -> creator.create(1L, 1L, 1L, big, null, CORRELATION_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds");
        verifyNoInteractions(problemRepository, languageRepository, submissionRepository,
                executionJobRepository, eventPublisher);
    }

    @Test
    void unpublishedProblemIsTreatedAs404() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(unpublishedProblem()));
        when(contestProblemRepository.findByProblemId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creator.create(1L, 1L, 2L, "code", null, CORRELATION_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(submissionRepository, eventPublisher);
    }

    @Test
    void disabledLanguageIsTreatedAs404() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(publishedProblem()));
        Language disabled = enabledLanguage();
        disabled.setEnabled(false);
        when(languageRepository.findById(2L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> creator.create(1L, 1L, 2L, "code", null, CORRELATION_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(submissionRepository, eventPublisher);
    }

    @Test
    void persistsSubmissionAndPublishesJobMessageEvent() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(publishedProblem()));
        Language language = enabledLanguage();
        ReflectionTestUtils.setField(language, "timeLimitMultiplier", new BigDecimal("1.50"));
        when(languageRepository.findById(2L)).thenReturn(Optional.of(language));
        when(userRepository.getReferenceById(7L)).thenReturn(new User("alice", "a@x.com", "h"));
        when(submissionRepository.saveAndFlush(any(Submission.class))).thenAnswer(invocation -> {
            Submission saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });
        when(testCaseRepository.findByProblemIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(
                persistedTestCase(11L, "in-1", true), persistedTestCase(12L, "in-2", false)));

        Submission submission = creator.create(7L, 1L, 2L, "int main(){}", "key-1", CORRELATION_ID);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(submission.getIdempotencyKey()).isEqualTo("key-1");

        ArgumentCaptor<ExecutionJob> job = ArgumentCaptor.forClass(ExecutionJob.class);
        verify(executionJobRepository).save(job.capture());
        assertThat(job.getValue().getSubmission()).isSameAs(submission);

        ArgumentCaptor<SubmissionCreatedEvent> event = ArgumentCaptor.forClass(SubmissionCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().correlationId()).isEqualTo(CORRELATION_ID);
        SubmissionJobMessage message = event.getValue().message();
        assertThat(message.submissionId()).isEqualTo(42L);
        assertThat(message.problemId()).isEqualTo(1L);
        assertThat(message.languageId()).isEqualTo(2);
        // PRD §26: limit = problem limit × language multiplier.
        assertThat(message.timeLimitMs()).isEqualTo(1_500);
        assertThat(message.memoryLimitKb()).isEqualTo(65_536);
        assertThat(message.testCaseIds()).containsExactly(11L, 12L);
    }

    @Test
    void unpublishedProblemWithoutContestIs404() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(unpublishedProblem()));
        when(contestProblemRepository.findByProblemId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creator.create(1L, 1L, 2L, "code", null, CORRELATION_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(submissionRepository, eventPublisher);
    }

    @Test
    void runningPublicContestUnregisteredUserIs403() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(unpublishedProblem()));
        when(contestProblemRepository.findByProblemId(1L))
                .thenReturn(Optional.of(contestProblem(runningContest(false))));
        when(userRepository.findById(7L)).thenReturn(Optional.of(new User("alice", "a@x.com", "h")));
        when(contestParticipantRepository.existsByContestIdAndUserId(5L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> creator.create(7L, 1L, 2L, "code", null, CORRELATION_ID))
                .isInstanceOf(ContestRegistrationRequiredException.class)
                .hasMessage("Register for this contest to submit");
        verifyNoInteractions(submissionRepository, eventPublisher);
    }

    @Test
    void runningPublicContestRegisteredUserSucceeds() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(unpublishedProblem()));
        when(contestProblemRepository.findByProblemId(1L))
                .thenReturn(Optional.of(contestProblem(runningContest(false))));
        User alice = new User("alice", "a@x.com", "h");
        when(userRepository.findById(7L)).thenReturn(Optional.of(alice));
        when(contestParticipantRepository.existsByContestIdAndUserId(5L, 7L)).thenReturn(true);
        when(languageRepository.findById(2L)).thenReturn(Optional.of(enabledLanguage()));
        when(userRepository.getReferenceById(7L)).thenReturn(alice);
        when(submissionRepository.saveAndFlush(any(Submission.class))).thenAnswer(invocation -> {
            Submission saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        Submission submission = creator.create(7L, 1L, 2L, "int main(){}", null, CORRELATION_ID);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        verify(submissionRepository).saveAndFlush(any(Submission.class));
        verify(eventPublisher).publishEvent(any(SubmissionCreatedEvent.class));
    }

    @Test
    void runningPrivateContestNonAdminIs404() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(unpublishedProblem()));
        when(contestProblemRepository.findByProblemId(1L))
                .thenReturn(Optional.of(contestProblem(runningContest(true))));
        when(userRepository.findById(7L)).thenReturn(Optional.of(new User("alice", "a@x.com", "h")));

        assertThatThrownBy(() -> creator.create(7L, 1L, 2L, "code", null, CORRELATION_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(contestParticipantRepository, never()).existsByContestIdAndUserId(any(), any());
        verifyNoInteractions(submissionRepository, eventPublisher);
    }

    @Test
    void runningPrivateContestAdminSucceeds() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(unpublishedProblem()));
        when(contestProblemRepository.findByProblemId(1L))
                .thenReturn(Optional.of(contestProblem(runningContest(true))));
        User admin = adminUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(languageRepository.findById(2L)).thenReturn(Optional.of(enabledLanguage()));
        when(userRepository.getReferenceById(7L)).thenReturn(admin);
        when(submissionRepository.saveAndFlush(any(Submission.class))).thenAnswer(invocation -> {
            Submission saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        Submission submission = creator.create(7L, 1L, 2L, "int main(){}", null, CORRELATION_ID);

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        verify(submissionRepository).saveAndFlush(any(Submission.class));
        verify(contestParticipantRepository, never()).existsByContestIdAndUserId(any(), any());
    }

    @Test
    void endedContestUnpublishedProblemIs404() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(unpublishedProblem()));
        when(contestProblemRepository.findByProblemId(1L)).thenReturn(Optional.of(contestProblem(endedContest())));

        assertThatThrownBy(() -> creator.create(7L, 1L, 2L, "code", null, CORRELATION_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(userRepository, contestParticipantRepository, submissionRepository, eventPublisher);
    }

    @Test
    void publishedProblemSkipsContestCheck() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(publishedProblem()));
        when(languageRepository.findById(2L)).thenReturn(Optional.of(enabledLanguage()));
        when(userRepository.getReferenceById(7L)).thenReturn(new User("alice", "a@x.com", "h"));
        when(submissionRepository.saveAndFlush(any(Submission.class))).thenAnswer(invocation -> {
            Submission saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        creator.create(7L, 1L, 2L, "int main(){}", null, CORRELATION_ID);

        verify(contestProblemRepository, never()).findByProblemId(any());
        verify(userRepository, never()).findById(any());
    }

    private Contest runningContest(boolean isPrivate) {
        Instant now = Instant.now();
        return contest(isPrivate, now.minusSeconds(3600), now.plusSeconds(3600));
    }

    private Contest endedContest() {
        Instant now = Instant.now();
        return contest(false, now.minusSeconds(7200), now.minusSeconds(3600));
    }

    private Contest contest(boolean isPrivate, Instant start, Instant end) {
        Contest contest = new Contest("Weekly", "desc", start, end, isPrivate, 60, null);
        ReflectionTestUtils.setField(contest, "id", 5L);
        return contest;
    }

    private ContestProblem contestProblem(Contest contest) {
        return new ContestProblem(contest, unpublishedProblem(), 100, 1);
    }

    private User adminUser() {
        User admin = new User("root", "root@x.com", "h");
        admin.grantRole(new Role(AppRole.ROLE_ADMIN.name()));
        return admin;
    }

    private TestCase persistedTestCase(Long id, String input, boolean sample) {
        TestCase testCase = new TestCase(input, input + "-out", sample, 0, 1);
        ReflectionTestUtils.setField(testCase, "id", id);
        return testCase;
    }

    private Problem publishedProblem() {
        Problem problem = new Problem("two-sum", "Two Sum", "statement", Difficulty.EASY, 1000, 65536, null);
        problem.setPublished(true);
        ReflectionTestUtils.setField(problem, "id", 1L);
        return problem;
    }

    private Problem unpublishedProblem() {
        Problem problem = new Problem("draft", "Draft", "statement", Difficulty.EASY, 1000, 65536, null);
        ReflectionTestUtils.setField(problem, "id", 1L);
        return problem;
    }

    private Language enabledLanguage() {
        Language language = new Language("Java 21", "Main.java", "javac Main.java", "java Main",
                "oj-java21", BigDecimal.ONE);
        ReflectionTestUtils.setField(language, "id", 2L);
        return language;
    }
}
