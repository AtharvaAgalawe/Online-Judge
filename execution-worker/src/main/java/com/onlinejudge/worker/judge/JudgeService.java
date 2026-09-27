package com.onlinejudge.worker.judge;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.enums.Verdict;
import com.onlinejudge.worker.execution.CompileOutcome;
import com.onlinejudge.worker.execution.ContainerRunner;
import com.onlinejudge.worker.execution.RunOutcome;

/**
 * The judging engine (PRD §20): compile once, run every test case in display order,
 * compare outputs, and hand the aggregated outcome to the final transactional write.
 *
 * <p>Sample test cases always all run (they are the user's debugging aid); hidden test
 * cases stop at the first non-accepted result. The submission's verdict is the first
 * failure observed — samples and hidden alike — or ACCEPTED when everything passes.
 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);
    private static final int STDERR_SNIPPET_LIMIT = 2_000;

    private final JudgingPlanLoader planLoader;
    private final ContainerRunner runner;
    private final SubmissionProgressService progress;
    private final RetryingResultFinalizer finalizer;

    public JudgeService(JudgingPlanLoader planLoader, ContainerRunner runner,
                        SubmissionProgressService progress, RetryingResultFinalizer finalizer) {
        this.planLoader = planLoader;
        this.runner = runner;
        this.progress = progress;
        this.finalizer = finalizer;
    }

    public void judge(SubmissionJobMessage message) {
        JudgingPlan plan = planLoader.load(message.submissionId());
        Path workspace = runner.createWorkspace();
        try {
            if (plan.compileCmd() != null) {
                markProgress(() -> progress.markCompiling(plan.submissionId()));
                CompileOutcome compile = runner.compile(workspace, plan.dockerImage(), plan.sourceFilename(),
                        plan.sourceCode(), plan.compileCmd());
                if (!compile.success()) {
                    finalizer.finalizeCompilationError(plan.submissionId(), compile.compilerOutput());
                    return;
                }
            } else {
                runner.stageSource(workspace, plan.sourceFilename(), plan.sourceCode());
            }
            runTestCases(plan, workspace);
        } finally {
            runner.deleteWorkspace(workspace);
        }
    }

    private void runTestCases(JudgingPlan plan, Path workspace) {
        markProgress(() -> progress.markRunning(plan.submissionId()));
        Path artifactsDir = workspace.resolve("artifacts");

        List<CaseOutcome> outcomes = new ArrayList<>();
        CaseOutcome firstFailure = null;
        for (JudgingPlan.PlannedTestCase testCase : plan.testCases()) {
            RunOutcome run = runner.run(artifactsDir, plan.dockerImage(), plan.runCmd(), testCase.input(),
                    Duration.ofMillis(plan.timeLimitMs()), plan.memoryLimitKb());
            Verdict verdict = VerdictClassifier.classify(run, testCase.expectedOutput());
            CaseOutcome outcome = new CaseOutcome(testCase.id(), testCase.sample(), verdict,
                    (int) run.durationMillis(), (int) run.memoryUsedKb(),
                    snippet(testCase.sample(), run.stdout()), snippet(testCase.sample(), run.stderr()));
            outcomes.add(outcome);

            if (verdict != Verdict.ACCEPTED) {
                if (firstFailure == null) {
                    firstFailure = outcome;
                }
                if (!testCase.sample()) {
                    break;
                }
            }
        }
        finalizer.finalizeJudged(aggregate(plan, outcomes, firstFailure));
    }

    private JudgingOutcome aggregate(JudgingPlan plan, List<CaseOutcome> outcomes, CaseOutcome firstFailure) {
        Verdict verdict = firstFailure == null ? Verdict.ACCEPTED : firstFailure.verdict();
        int maxTimeMs = outcomes.stream().mapToInt(CaseOutcome::timeUsedMs).max().orElse(0);
        int maxMemoryKb = outcomes.stream().mapToInt(CaseOutcome::memoryUsedKb).max().orElse(0);
        return new JudgingOutcome(
                plan.submissionId(),
                VerdictClassifier.terminalStatus(verdict),
                verdict,
                maxTimeMs,
                maxMemoryKb,
                firstFailure == null ? null : firstFailure.testCaseId(),
                errorMessage(firstFailure),
                outcomes);
    }

    /** Hidden test content never leaks; the user's own stderr is kept for sample failures only (PRD §17). */
    private static String errorMessage(CaseOutcome failure) {
        if (failure == null || !failure.sample() || failure.verdict() != Verdict.RUNTIME_ERROR) {
            return null;
        }
        String stderr = failure.stderrSnippet();
        return stderr == null || stderr.length() <= STDERR_SNIPPET_LIMIT
                ? stderr
                : stderr.substring(0, STDERR_SNIPPET_LIMIT) + "\n[truncated]";
    }

    private static String snippet(boolean sample, String text) {
        if (!sample) {
            return null;
        }
        return text.length() <= 2_000 ? text : text.substring(0, 2_000) + "\n[truncated]";
    }

    private void markProgress(Runnable marker) {
        try {
            marker.run();
        } catch (RuntimeException e) {
            // Swallowing is correct because progress markers are advisory; the final
            // write is the authoritative record and will catch the status up itself.
            log.warn("Could not record judging progress: {}", e.getMessage());
        }
    }
}
