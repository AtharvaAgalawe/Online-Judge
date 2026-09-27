package com.onlinejudge.backend.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.onlinejudge.backend.service.SubmissionCreatedEvent;
import com.onlinejudge.backend.service.SubmissionService;
import com.onlinejudge.common.dto.SubmissionJobMessage;
import com.onlinejudge.common.messaging.MessagingTopology;

@ExtendWith(MockitoExtension.class)
class SubmissionJobPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private SubmissionService submissionService;

    private SubmissionJobPublisher publisher;
    private SubmissionCreatedEvent event;

    @BeforeEach
    void setUp() {
        publisher = new SubmissionJobPublisher(rabbitTemplate, submissionService);
        event = new SubmissionCreatedEvent(
                new SubmissionJobMessage(42L, 1L, 2, 1_500, 65_536, List.of(11L, 12L)), "cid-7");
    }

    @Test
    void publishesPersistentMessageWithCorrelationIdThenMarksQueued() throws Exception {
        publisher.onSubmissionCreated(event);

        ArgumentCaptor<MessagePostProcessor> postProcessor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(MessagingTopology.EXCHANGE), eq(MessagingTopology.ROUTING_KEY),
                eq(event.message()), postProcessor.capture());

        Message message = postProcessor.getValue()
                .postProcessMessage(new Message(new byte[0], new MessageProperties()));
        Object correlationId = message.getMessageProperties().getHeader("correlationId");
        assertThat(correlationId).isEqualTo("cid-7");
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);

        verify(submissionService).markQueued(42L);
    }

    @Test
    void publishFailureIsLoggedAndNotRethrownAndStatusIsNotFlipped() {
        doThrow(new AmqpException("broker down")).when(rabbitTemplate)
                .convertAndSend(any(), any(), any(), any(MessagePostProcessor.class));

        publisher.onSubmissionCreated(event);

        verifyNoInteractions(submissionService);
    }

    @Test
    void statusFlipFailureDoesNotPropagate() {
        doThrow(new IllegalStateException("version conflict")).when(submissionService).markQueued(42L);

        publisher.onSubmissionCreated(event);
    }
}

