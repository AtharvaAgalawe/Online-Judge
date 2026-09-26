package com.onlinejudge.common.dto;

import java.util.List;

/**
 * Job message published to RabbitMQ after the submission transaction commits
 * (PRD §12). Immutable (AGENTS.md §5). The correlationId travels as a message
 * header for MDC propagation (PRD §27), so it is not part of the payload.
 *
 * @param submissionId   judged submission
 * @param problemId       problem the submission belongs to
 * @param languageId      language runtime to use
 * @param timeLimitMs     problem time limit already multiplied by the language factor
 * @param memoryLimitKb   problem memory limit
 * @param testCaseIds     ordered test case ids to execute
 */
public record SubmissionJobMessage(
        long submissionId,
        long problemId,
        int languageId,
        int timeLimitMs,
        int memoryLimitKb,
        List<Long> testCaseIds) {

    public SubmissionJobMessage {
        testCaseIds = List.copyOf(testCaseIds);
    }
}
