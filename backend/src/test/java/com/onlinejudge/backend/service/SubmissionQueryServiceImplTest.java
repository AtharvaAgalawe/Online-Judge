package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.onlinejudge.backend.api.dto.SubmissionDetailResponse;
import com.onlinejudge.backend.api.dto.SubmissionStatusResponse;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.SubmissionRepository;
import com.onlinejudge.backend.repository.SubmissionResultRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.common.entity.SubmissionResult;
import com.onlinejudge.common.entity.TestCase;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.Difficulty;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;

@ExtendWith(MockitoExtension.class)
class SubmissionQueryServiceImplTest {

    private static final long SUBMISSION_ID = 42L;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionResultRepository submissionResultRepository;

    @Mock
    private UserRepository userRepository;

    private SubmissionQueryServiceImpl service;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        service = new SubmissionQueryServiceImpl(submissionRepository, submissionResultRepository, userRepository);
        alice = user(1L, "alice");
        bob = user(2L, "bob");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerReadsOwnJudgedDetailWithFailedTestCaseAndResults() {
        authenticate(alice.getUsername(), "ROLE_USER");
        Submission submission = judgedSubmission(alice);
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        TestCase failedCase = testCase(1, false);
        when(submissionResultRepository.findBySubmissionIdOrderByTestCaseDisplayOrderAsc(SUBMISSION_ID))
                .thenReturn(List.of(new SubmissionResult(submission, failedCase, Verdict.WRONG_ANSWER, 12, 512, null)));

        SubmissionDetailResponse detail = service.getDetail(SUBMISSION_ID);

        assertThat(detail.sourceCode()).isEqualTo("print('secret')");
        assertThat(detail.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(detail.failedTestCase()).isNotNull();
        assertThat(detail.failedTestCase().index()).isEqualTo(2);
        assertThat(detail.failedTestCase().isSample()).isFalse();
        assertThat(detail.results()).hasSize(1);
        assertThat(detail.results().get(0).index()).isEqualTo(2);
    }

    @Test
    void anotherUserIsForbidden() {
        authenticate(bob.getUsername(), "ROLE_USER");
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(judgedSubmission(alice)));

        assertThatThrownBy(() -> service.getDetail(SUBMISSION_ID)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getStatus(SUBMISSION_ID)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminReadsAnyoneDetail() {
        authenticate("root", "ROLE_ADMIN");
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(judgedSubmission(alice)));
        when(submissionResultRepository.findBySubmissionIdOrderByTestCaseDisplayOrderAsc(SUBMISSION_ID))
                .thenReturn(List.of());

        assertThat(service.getDetail(SUBMISSION_ID).id()).isEqualTo(SUBMISSION_ID);
    }

    @Test
    void statusResponseCarriesNullVerdictBeforeTerminal() {
        authenticate(alice.getUsername(), "ROLE_USER");
        Submission submission = new Submission(alice, problem(), language(), "code", null);
        ReflectionTestUtils.setField(submission, "id", SUBMISSION_ID);
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));

        SubmissionStatusResponse status = service.getStatus(SUBMISSION_ID);

        assertThat(status.status()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(status.verdict()).isNull();
    }

    @Test
    void unknownSubmissionIs404() {
        authenticate(alice.getUsername(), "ROLE_USER");
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(SUBMISSION_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void historyDefaultsToTheCallersOwnSubmissions() {
        authenticate(alice.getUsername(), "ROLE_USER");
        when(userRepository.findByUsername(alice.getUsername())).thenReturn(Optional.of(alice));
        when(submissionRepository.findByUserIdOrderBySubmittedAtDesc(eq(alice.getId()), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(judgedSubmission(alice))));

        var page = service.history(null, null, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).problemSlug()).isEqualTo("two-sum");
    }

    @Test
    void historyProblemFilterUsesTheFilteredQuery() {
        authenticate(alice.getUsername(), "ROLE_USER");
        when(userRepository.findByUsername(alice.getUsername())).thenReturn(Optional.of(alice));
        when(submissionRepository.findByProblemIdAndUserIdOrderBySubmittedAtDesc(
                eq(7L), eq(alice.getId()), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var page = service.history(7L, null, 0, 20);

        assertThat(page.content()).isEmpty();
    }

    @Test
    void nonAdminCannotFilterByAnotherUser() {
        authenticate(alice.getUsername(), "ROLE_USER");

        assertThatThrownBy(() -> service.history(null, 99L, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminMayFilterByAnyUser() {
        authenticate("root", "ROLE_ADMIN");
        when(submissionRepository.findByUserIdOrderBySubmittedAtDesc(eq(99L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.history(null, 99L, 0, 20);

        ArgumentCaptor<PageRequest> pageRequest = ArgumentCaptor.forClass(PageRequest.class);
        verify(submissionRepository).findByUserIdOrderBySubmittedAtDesc(eq(99L), pageRequest.capture());
        assertThat(pageRequest.getValue().getPageSize()).isEqualTo(20);
    }

    private void authenticate(String username, String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }

    private static User user(Long id, String username) {
        User user = new User(username, username + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Submission judgedSubmission(User owner) {
        Submission submission = new Submission(owner, problem(), language(), "print('secret')", null);
        ReflectionTestUtils.setField(submission, "id", SUBMISSION_ID);
        submission.transitionTo(SubmissionStatus.QUEUED);
        submission.transitionTo(SubmissionStatus.PICKED_UP);
        submission.transitionTo(SubmissionStatus.COMPILING);
        submission.transitionTo(SubmissionStatus.RUNNING);
        submission.transitionTo(SubmissionStatus.EVALUATING);
        submission.applyTerminalOutcome(SubmissionStatus.COMPLETED, Verdict.WRONG_ANSWER,
                12, 512, testCase(1, false), null);
        return submission;
    }

    private static TestCase testCase(int displayOrder, boolean sample) {
        return new TestCase("in", "out", sample, displayOrder, 1);
    }

    private static Problem problem() {
        return new Problem("two-sum", "Two Sum", "statement", Difficulty.EASY, 1000, 65536, null);
    }

    private static Language language() {
        return new Language("Python 3.12", "main.py", null, "python3 main.py", "oj-python312", BigDecimal.ONE);
    }
}
