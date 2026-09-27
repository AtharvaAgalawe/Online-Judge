package com.onlinejudge.common.messaging;

/**
 * The messaging contract shared by the backend (producer) and the execution-worker
 * (consumer) — single source of truth so the two sides can never drift (AGENTS.md §2).
 *
 * <p>Topology from PRD §19: a direct exchange routes jobs to the work queue; the retry
 * queue holds failed messages for a TTL and dead-letters them back to the work queue;
 * the dead-letter queue parks poison messages for operators.
 */
public final class MessagingTopology {

    private MessagingTopology() {
    }

    public static final String EXCHANGE = "submission.exchange";

    public static final String JOBS_QUEUE = "submission.jobs";
    public static final String RETRY_QUEUE = "submission.retry";
    public static final String DEAD_LETTER_QUEUE = "submission.dlq";

    public static final String ROUTING_KEY = "submit";
    public static final String RETRY_ROUTING_KEY = "retry";
    public static final String DEAD_ROUTING_KEY = "dead";

    /** Backoff applied by the retry queue before a message returns to the work queue. */
    public static final int RETRY_TTL_MS = 5_000;

    /** Message header carrying the correlation id across API → queue → worker (PRD §27). */
    public static final String CORRELATION_ID_HEADER = "correlationId";
}
