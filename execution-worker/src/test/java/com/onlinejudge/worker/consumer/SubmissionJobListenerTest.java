package com.onlinejudge.worker.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.messaging.MessagingTopology;
import com.onlinejudge.worker.judge.JudgeService;
import com.rabbitmq.client.Channel;

@ExtendWith(MockitoExtension.class)
class SubmissionJobListenerTest {

    private static final long DELIVERY_TAG = 7L;

    @Mock
    private JobClaimService claimService;

    @Mock
    private JudgeService judgeService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Channel channel;

    private SubmissionJobListener listener;
    private SubmissionJobMessage message;

    @BeforeEach
    void setUp() {
        listener = new SubmissionJobListener(claimService, judgeService, rabbitTemplate);
        message = new SubmissionJobMessage(42L, 1L, 2, 1_000, 65_536, List.of(11L));
    }

    @Test
    void duplicateDeliveryIsAckedWithoutJudging() throws Exception {
        when(claimService.claim(42L)).thenReturn(JobClaimService.ClaimResult.DUPLICATE);

        listener.onSubmissionJob(message, channel, DELIVERY_TAG);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verifyNoInteractions(judgeService);
    }

    @Test
    void alreadyTerminalSubmissionIsAckedWithoutJudging() throws Exception {
        when(claimService.claim(42L)).thenReturn(JobClaimService.ClaimResult.ALREADY_TERMINAL);

        listener.onSubmissionJob(message, channel, DELIVERY_TAG);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verifyNoInteractions(judgeService);
    }

    @Test
    void retriesExhaustedParksMessageInDeadLetterQueueThenAcks() throws Exception {
        when(claimService.claim(42L)).thenReturn(JobClaimService.ClaimResult.RETRIES_EXHAUSTED);

        listener.onSubmissionJob(message, channel, DELIVERY_TAG);

        verify(rabbitTemplate).convertAndSend(eq(MessagingTopology.EXCHANGE),
                eq(MessagingTopology.DEAD_ROUTING_KEY), eq(message), any(MessagePostProcessor.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
        verifyNoInteractions(judgeService);
    }

    @Test
    void successfulJudgingIsAcked() throws Exception {
        when(claimService.claim(42L)).thenReturn(JobClaimService.ClaimResult.CLAIMED);

        listener.onSubmissionJob(message, channel, DELIVERY_TAG);

        verify(judgeService).judge(message);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void failedJudgingReleasesLeaseAndRoutesThroughRetryQueueThenAcks() throws Exception {
        when(claimService.claim(42L)).thenReturn(JobClaimService.ClaimResult.CLAIMED);
        doThrow(new IllegalStateException("db exploded")).when(judgeService).judge(message);

        listener.onSubmissionJob(message, channel, DELIVERY_TAG);

        verify(claimService).release(42L);
        verify(rabbitTemplate).convertAndSend(eq(MessagingTopology.EXCHANGE),
                eq(MessagingTopology.RETRY_ROUTING_KEY), eq(message), any(MessagePostProcessor.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void failedRequeueNacksForRedeliveryInsteadOfAcking() throws Exception {
        when(claimService.claim(42L)).thenReturn(JobClaimService.ClaimResult.CLAIMED);
        doThrow(new IllegalStateException("db exploded")).when(judgeService).judge(message);
        doThrow(new IllegalStateException("broker exploded")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        listener.onSubmissionJob(message, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void claimFailureNacksForRedelivery() throws Exception {
        when(claimService.claim(42L)).thenThrow(new IllegalStateException("db down"));

        listener.onSubmissionJob(message, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verifyNoInteractions(judgeService);
    }
}

