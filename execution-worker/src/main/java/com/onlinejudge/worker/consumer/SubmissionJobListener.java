package com.onlinejudge.worker.consumer;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.messaging.MessagingTopology;
import com.onlinejudge.worker.judge.StubJudgeService;
import com.rabbitmq.client.Channel;

/**
 * The judging consumer (PRD §12, §19). Manual ack mode: a message is acknowledged only
 * after the database work it caused has committed. Every branch ends in an explicit
 * ack/nack — an unhandled exception leaves the message unacked for redelivery instead of
 * silently losing it.
 */
@Component
public class SubmissionJobListener {

    private static final Logger log = LoggerFactory.getLogger(SubmissionJobListener.class);

    private final JobClaimService claimService;
    private final StubJudgeService judgeService;
    private final RabbitTemplate rabbitTemplate;

    public SubmissionJobListener(JobClaimService claimService, StubJudgeService judgeService,
                                 RabbitTemplate rabbitTemplate) {
        this.claimService = claimService;
        this.judgeService = judgeService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = MessagingTopology.JOBS_QUEUE)
    public void onSubmissionJob(SubmissionJobMessage message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        long submissionId = message.submissionId();

        JobClaimService.ClaimResult result;
        try {
            result = claimService.claim(submissionId);
        } catch (RuntimeException e) {
            // Infrastructure failure before any judging work: requeue rather than lose the job.
            log.error("Could not claim submission {}; requeueing", submissionId, e);
            channel.basicNack(deliveryTag, false, true);
            return;
        }

        switch (result) {
            case DUPLICATE -> {
                log.debug("Duplicate delivery for submission {}; another worker holds the lease", submissionId);
                channel.basicAck(deliveryTag, false);
            }
            case ALREADY_TERMINAL -> channel.basicAck(deliveryTag, false);
            case RETRIES_EXHAUSTED -> {
                parkInDeadLetterQueue(message);
                channel.basicAck(deliveryTag, false);
            }
            case CLAIMED -> process(message, channel, deliveryTag);
        }
    }

    private void process(SubmissionJobMessage message, Channel channel, long deliveryTag) throws IOException {
        long submissionId = message.submissionId();
        try {
            judgeService.judge(message);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            log.error("Judging failed for submission {}; routing through the retry queue", submissionId, e);
            try {
                claimService.release(submissionId);
                rabbitTemplate.convertAndSend(MessagingTopology.EXCHANGE, MessagingTopology.RETRY_ROUTING_KEY,
                        message, posted -> {
                            posted.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                            return posted;
                        });
            } catch (RuntimeException requeueFailure) {
                // Could not hand the job to the retry queue: keep it in-flight for redelivery.
                log.error("Could not requeue submission {}; nacking for redelivery", submissionId, requeueFailure);
                channel.basicNack(deliveryTag, false, true);
                return;
            }
            channel.basicAck(deliveryTag, false);
        }
    }

    private void parkInDeadLetterQueue(SubmissionJobMessage message) {
        rabbitTemplate.convertAndSend(MessagingTopology.EXCHANGE, MessagingTopology.DEAD_ROUTING_KEY, message,
                posted -> {
                    posted.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return posted;
                });
        log.error("Submission {} exhausted retries; parked in {}", message.submissionId(),
                MessagingTopology.DEAD_LETTER_QUEUE);
    }
}
