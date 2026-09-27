package com.onlinejudge.worker.judge;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.common.entity.Language;
import com.onlinejudge.common.entity.Problem;
import com.onlinejudge.common.entity.Submission;
import com.onlinejudge.worker.repository.SubmissionRepository;
import com.onlinejudge.worker.repository.TestCaseRepository;

/**
 * Loads the judging snapshot in one short read-only transaction; everything the judge
 * needs is then plain data, so the long container phase runs with no transaction (and
 * no database connection) held open.
 */
@Service
public class JudgingPlanLoader {

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;

    public JudgingPlanLoader(SubmissionRepository submissionRepository, TestCaseRepository testCaseRepository) {
        this.submissionRepository = submissionRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional(readOnly = true)
    public JudgingPlan load(long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Submission vanished before judging: %d".formatted(submissionId)));
        Problem problem = submission.getProblem();
        Language language = submission.getLanguage();

        List<JudgingPlan.PlannedTestCase> testCases = testCaseRepository
                .findByProblemIdOrderByDisplayOrderAsc(problem.getId()).stream()
                .map(testCase -> new JudgingPlan.PlannedTestCase(testCase.getId(), testCase.getInput(),
                        testCase.getExpectedOutput(), testCase.isSample(), testCase.getDisplayOrder()))
                .toList();

        return new JudgingPlan(
                submissionId,
                submission.getSourceCode(),
                language.getDockerImage(),
                language.getSourceFilename(),
                language.getCompileCmd(),
                language.getRunCmd(),
                effectiveTimeLimitMs(problem, language),
                problem.getMemoryLimitKb(),
                testCases);
    }

    private static int effectiveTimeLimitMs(Problem problem, Language language) {
        return BigDecimal.valueOf(problem.getTimeLimitMs())
                .multiply(language.getTimeLimitMultiplier())
                .intValue();
    }
}
