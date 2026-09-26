package com.onlinejudge.backend.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinejudge.backend.service.SubmissionCreatedEvent;
import com.onlinejudge.backend.service.SubmissionService;
import com.onlinejudge.common.messaging.MessagingTopology;

/**
 * Publishes the job message to RabbitMQ strictly after the submission transaction has
 * committed — a rollback means no message, ever (PRD §12, §13). Publish failures are
 * infrastructure failures: the submission stays persisted (SUBITTED) and is surfaced for
 * reconciliation rather than failing the already-acknowledged API call.
 */
@Component
public class SubmissionJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(SubmissionJobPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final SubmissionService submissionService;

    public SubmissionJobPublisher(RabbitTemplate rabbitTemplate, SubmissionService submissionService) {
        this.rabbitTemplate = rabbitTemplate;
        this.submissionService = submissionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionCreated(SubmissionCreatedEvent event) {
        long submissionId = event.message().submissionId();
        try {
            rabbitTemplate.convertAndSend(MessagingTopology.EXCHANGE, MessagingTopology.ROUTING_KEY,
                    event.message(), message -> {
                        message.getMessageProperties().setHeader(
                                MessagingTopology.CORRELATION_ID_HEADER, event.correlationId());
                        // Durable queue + persistent message: the job survives broker restarts (PRD §8).
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    });
        } catch (RuntimeException e) {
            log.error("Failed to publish job for submission {}; it remains SUBMITTED for reconciliation",
                    submissionId, e);
            return;
        }

        try {
            submissionService.markQueued(submissionId);
        } catch (RuntimeException e) {
            // The message is already published; the status is advisory and a reconciliation
            // pass corrects it. Never let this mask the successful publish.
            log.warn("Published job for submission {} but failed to mark it QUEUED: {}",
                    submissionId, e.getMessage());
        }
    }
}
