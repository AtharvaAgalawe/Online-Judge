package com.onlinejudge.worker.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.enums.SubmissionStatus;
import com.onlinejudge.common.enums.Verdict;
import com.onlinejudge.worker.execution.CompileOutcome;
import com.onlinejudge.worker.execution.ContainerRunner;
import com.onlinejudge.worker.execution.RunOutcome;

@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

    private static final long SUBMISSION_ID = 42L;
    private static final String IMAGE = "oj-python312";
    private static final String RUN_CMD = "python3 main.py";
    private static final int MEMORY_KB = 65_536;
    private static final int TIME_MS = 1_000;

    @Mock
    private JudgingPlanLoader planLoader;

    @Mock
    private ContainerRunner runner;

    @Mock
    private SubmissionProgressService progress;

    @Mock
    private RetryingResultFinalizer finalizer;

    private JudgeService judgeService;

    @BeforeEach
    void setUp() {
        judgeService = new JudgeService(planLoader, runner, progress, finalizer);
        when(runner.createWorkspace()).thenReturn(Path.of("target", "judge-test-ws"));
        org.mockito.Mockito.lenient().when(runner.stageSource(any(), anyString(), anyString()))
                .thenReturn(Path.of("target", "judge-test-ws", "artifacts"));
    }

    @Test
    void compilationFailureWritesCompilationErrorAndRunsNoTestCases() {
        JudgingPlan plan = plan("javac -d /box/out /box/src/Main.java", testCase(11L, "in", "out", true, 0));
        when(planLoader.load(SUBMISSION_ID)).thenReturn(plan);
        when(runner.compile(any(), eq(IMAGE), anyString(), anyString(), anyString()))
                .thenReturn(new CompileOutcome(false, "Main.java:1: error: ';' expected"));

        judgeService.judge(message());

        verify(progress).markCompiling(SUBMISSION_ID);
        verify(progress, never()).markRunning(anyLong());
        verify(runner, never()).run(any(), anyString(), anyString(), anyString(), any(), anyInt());
        verify(finalizer).finalizeCompilationError(eq(SUBMISSION_ID), anyString());
        verify(finalizer, never()).finalizeJudged(any());
    }

    @Test
    void interpretedLanguageSkipsTheCompilePhase() {
        JudgingPlan plan = plan(null, testCase(11L, "1 2", "3", true, 0));
        when(planLoader.load(SUBMISSION_ID)).thenReturn(plan);
        when(runner.run(any(), eq(IMAGE), eq(RUN_CMD), eq("1 2"), any(Duration.class), eq(MEMORY_KB)))
                .thenReturn(accepted("3\n"));

        judgeService.judge(message());

        verify(runner, never()).compile(any(), anyString(), anyString(), anyString(), anyString());
        verify(runner).stageSource(any(), eq("main.py"), eq("print(1)"));
        verify(progress, never()).markCompiling(SUBMISSION_ID);
        verify(progress).markRunning(SUBMISSION_ID);
        verify(finalizer).finalizeJudged(any());
    }

    @Test
    void stopsAtFirstFailingHiddenTestCase() {
        JudgingPlan plan = plan(null,
                testCase(11L, "sample-in", "2", true, 0),
                testCase(12L, "hidden-in", "5", false, 1),
                testCase(13L, "later-in", "9", false, 2));
        when(planLoader.load(SUBMISSION_ID)).thenReturn(plan);
        when(runner.run(any(), anyString(), anyString(), eq("sample-in"), any(), anyInt()))
                .thenReturn(accepted("2\n"));
        when(runner.run(any(), anyString(), anyString(), eq("hidden-in"), any(), anyInt()))
                .thenReturn(accepted("999\n"));

        judgeService.judge(message());

        verify(runner, never()).run(any(), anyString(), anyString(), eq("later-in"), any(), anyInt());
        JudgingOutcome outcome = capturedOutcome();
        assertThat(outcome.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(outcome.terminalStatus()).isEqualTo(SubmissionStatus.COMPLETED);
        assertThat(outcome.failedTestCaseId()).isEqualTo(12L);
        assertThat(outcome.results()).hasSize(2);
        assertThat(outcome.results().get(1).stdoutSnippet())
                .as("hidden test output is never stored")
                .isNull();
    }

    @Test
    void sampleTestCasesAllRunEvenWhenTheyFail() {
        JudgingPlan plan = plan(null,
                testCase(11L, "s1", "2", true, 0),
                testCase(12L, "s2", "3", true, 1),
                testCase(13L, "h1", "5", false, 2));
        when(planLoader.load(SUBMISSION_ID)).thenReturn(plan);
        when(runner.run(any(), anyString(), anyString(), eq("s1"), any(), anyInt()))
                .thenReturn(accepted("wrong\n"));
        when(runner.run(any(), anyString(), anyString(), eq("s2"), any(), anyInt()))
                .thenReturn(accepted("3\n"));
        when(runner.run(any(), anyString(), anyString(), eq("h1"), any(), anyInt()))
                .thenReturn(accepted("5\n"));

        judgeService.judge(message());

        JudgingOutcome outcome = capturedOutcome();
        assertThat(outcome.results()).hasSize(3);
        assertThat(outcome.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(outcome.failedTestCaseId()).isEqualTo(11L);
        assertThat(outcome.results().get(0).stdoutSnippet()).isEqualTo("wrong\n");
    }

    @Test
    void aggregatesMaximumTimeAndMemoryAcrossExecutedTestCases() {
        JudgingPlan plan = plan(null,
                testCase(11L, "a", "1", true, 0),
                testCase(12L, "b", "2", false, 1));
        when(planLoader.load(SUBMISSION_ID)).thenReturn(plan);
        when(runner.run(any(), anyString(), anyString(), eq("a"), any(), anyInt()))
                .thenReturn(new RunOutcome(0, false, false, "1\n", "", 100, 4_096));
        when(runner.run(any(), anyString(), anyString(), eq("b"), any(), anyInt()))
                .thenReturn(new RunOutcome(0, false, false, "2\n", "", 250, 2_048));

        judgeService.judge(message());

        JudgingOutcome outcome = capturedOutcome();
        assertThat(outcome.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(outcome.terminalStatus()).isEqualTo(SubmissionStatus.COMPLETED);
        assertThat(outcome.timeUsedMs()).isEqualTo(250);
        assertThat(outcome.memoryUsedKb()).isEqualTo(4_096);
        assertThat(outcome.failedTestCaseId()).isNull();
    }

    @Test
    void timedOutRunBecomesTerminalTimeLimitExceeded() {
        JudgingPlan plan = plan(null, testCase(11L, "a", "1", true, 0));
        when(planLoader.load(SUBMISSION_ID)).thenReturn(plan);
        when(runner.run(any(), anyString(), anyString(), eq("a"), any(), anyInt()))
                .thenReturn(new RunOutcome(137, true, false, "", "", 3_000, 1_024));

        judgeService.judge(message());

        JudgingOutcome outcome = capturedOutcome();
        assertThat(outcome.verdict()).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
        assertThat(outcome.terminalStatus()).isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
        assertThat(outcome.failedTestCaseId()).isEqualTo(11L);
    }

    @Test
    void sampleRuntimeErrorKeepsTheUsersStderrForDebugging() {
        JudgingPlan plan = plan(null, testCase(11L, "a", "1", true, 0));
        when(planLoader.load(SUBMISSION_ID)).thenReturn(plan);
        when(runner.run(any(), anyString(), anyString(), eq("a"), any(), anyInt()))
                .thenReturn(new RunOutcome(1, false, false, "", "Traceback: boom", 200, 1_024));

        judgeService.judge(message());

        JudgingOutcome outcome = capturedOutcome();
        assertThat(outcome.verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
        assertThat(outcome.errorMessage()).contains("Traceback: boom");
        assertThat(outcome.results().get(0).stdoutSnippet()).isEqualTo("");
    }

    @Test
    void hiddenRuntimeErrorNeverExposesStderr() {
        JudgingPlan plan = plan(null, testCase(11L, "a", "1", false, 0));
        when(planLoader.load(SUBMISSION_ID)).thenReturn(plan);
        when(runner.run(any(), anyString(), anyString(), eq("a"), any(), anyInt()))
                .thenReturn(new RunOutcome(1, false, false, "", "Traceback: boom", 200, 1_024));

        judgeService.judge(message());

        JudgingOutcome outcome = capturedOutcome();
        assertThat(outcome.errorMessage()).isNull();
    }

    @Test
    void progressMarkerFailuresDoNotBreakJudging() {
        JudgingPlan plan = plan(null, testCase(11L, "a", "1", true, 0));
        when(planLoader.load(SUBMISSION_ID)).thenReturn(plan);
        org.mockito.Mockito.doThrow(new IllegalStateException("db blip"))
                .when(progress).markRunning(SUBMISSION_ID);
        when(runner.run(any(), anyString(), anyString(), eq("a"), any(), anyInt()))
                .thenReturn(accepted("1\n"));

        judgeService.judge(message());

        verify(finalizer).finalizeJudged(any());
    }

    private JudgingOutcome capturedOutcome() {
        ArgumentCaptor<JudgingOutcome> captor = ArgumentCaptor.forClass(JudgingOutcome.class);
        verify(finalizer).finalizeJudged(captor.capture());
        return captor.getValue();
    }

    private JudgingPlan plan(String compileCmd, JudgingPlan.PlannedTestCase... testCases) {
        return new JudgingPlan(SUBMISSION_ID, "print(1)", IMAGE, "main.py", compileCmd, RUN_CMD,
                TIME_MS, MEMORY_KB, List.of(testCases));
    }

    private JudgingPlan.PlannedTestCase testCase(Long id, String input, String expected, boolean sample, int order) {
        return new JudgingPlan.PlannedTestCase(id, input, expected, sample, order);
    }

    private RunOutcome accepted(String stdout) {
        return new RunOutcome(0, false, false, stdout, "", 120, 1_024);
    }

    private SubmissionJobMessage message() {
        return new SubmissionJobMessage(SUBMISSION_ID, 1L, 2, TIME_MS, MEMORY_KB, List.of());
    }
}


