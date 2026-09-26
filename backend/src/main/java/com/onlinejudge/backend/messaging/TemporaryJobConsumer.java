package com.onlinejudge.backend.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.onlinejudge.common.dto.SubmissionJobMessage;

/**
 * TEMPORARY (Phase 6): logging-only consumer that proves end-to-end delivery through the
 * queue. The real consumer is the execution-worker (Phase 7), which replaces this class —
 * do not build judging behaviour on it.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.stub-consumer.enabled", havingValue = "true",
        matchIfMissing = true)
public class TemporaryJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(TemporaryJobConsumer.class);

    @RabbitListener(queues = RabbitTopologyConfig.JOBS_QUEUE)
    public void onJob(SubmissionJobMessage job) {
        log.info("Stub consumer received job for submission {} ({} test cases, problem {})",
                job.submissionId(), job.testCaseIds().size(), job.problemId());
    }
}
