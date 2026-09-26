package com.onlinejudge.backend.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queue topology from PRD §19. Declared idempotently by RabbitAdmin at startup.
 *
 * <ul>
 *   <li>{@code submission.jobs} — the work queue the execution-worker consumes.</li>
 *   <li>{@code submission.retry} — no consumers; messages sit for the TTL and are
 *       dead-lettered straight back to the jobs queue (queue-level backoff).</li>
 *   <li>{@code submission.dlq} — poison messages after retries are exhausted.</li>
 * </ul>
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String EXCHANGE = "submission.exchange";
    public static final String JOBS_QUEUE = "submission.jobs";
    public static final String RETRY_QUEUE = "submission.retry";
    public static final String DEAD_LETTER_QUEUE = "submission.dlq";

    public static final String ROUTING_KEY = "submit";
    public static final String RETRY_ROUTING_KEY = "retry";
    public static final String DEAD_ROUTING_KEY = "dead";

    public static final int RETRY_TTL_MS = 5_000;

    @Bean
    DirectExchange submissionExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    Queue submissionJobsQueue() {
        return QueueBuilder.durable(JOBS_QUEUE).build();
    }

    @Bean
    Queue submissionRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .ttl(RETRY_TTL_MS)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY)
                .build();
    }

    @Bean
    Queue submissionDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding submissionJobsBinding() {
        return BindingBuilder.bind(submissionJobsQueue()).to(submissionExchange()).with(ROUTING_KEY);
    }

    @Bean
    Binding submissionRetryBinding() {
        return BindingBuilder.bind(submissionRetryQueue()).to(submissionExchange()).with(RETRY_ROUTING_KEY);
    }

    @Bean
    Binding submissionDeadLetterBinding() {
        return BindingBuilder.bind(submissionDeadLetterQueue()).to(submissionExchange()).with(DEAD_ROUTING_KEY);
    }

    /** JSON payloads for the job messages (bucket: the shared SubmissionJobMessage record). */
    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
