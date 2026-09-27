package com.onlinejudge.backend.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.onlinejudge.common.messaging.MessagingTopology;

/**
 * Queue topology from PRD §19; names are the shared contract in
 * {@link MessagingTopology}. Declared idempotently by RabbitAdmin at startup — the
 * worker declares the same topology on its side, and brokers tolerate both.
 */
@Configuration
public class RabbitTopologyConfig {

    @Bean
    DirectExchange submissionExchange() {
        return ExchangeBuilder.directExchange(MessagingTopology.EXCHANGE).durable(true).build();
    }

    @Bean
    Queue submissionJobsQueue() {
        return QueueBuilder.durable(MessagingTopology.JOBS_QUEUE).build();
    }

    @Bean
    Queue submissionRetryQueue() {
        return QueueBuilder.durable(MessagingTopology.RETRY_QUEUE)
                .ttl(MessagingTopology.RETRY_TTL_MS)
                .deadLetterExchange(MessagingTopology.EXCHANGE)
                .deadLetterRoutingKey(MessagingTopology.ROUTING_KEY)
                .build();
    }

    @Bean
    Queue submissionDeadLetterQueue() {
        return QueueBuilder.durable(MessagingTopology.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding submissionJobsBinding() {
        return BindingBuilder.bind(submissionJobsQueue()).to(submissionExchange())
                .with(MessagingTopology.ROUTING_KEY);
    }

    @Bean
    Binding submissionRetryBinding() {
        return BindingBuilder.bind(submissionRetryQueue()).to(submissionExchange())
                .with(MessagingTopology.RETRY_ROUTING_KEY);
    }

    @Bean
    Binding submissionDeadLetterBinding() {
        return BindingBuilder.bind(submissionDeadLetterQueue()).to(submissionExchange())
                .with(MessagingTopology.DEAD_ROUTING_KEY);
    }

    /** JSON payloads for the job messages (bucket: the shared SubmissionJobMessage record). */
    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        // Deserialization instantiates classes named in the message's __TypeId__ header.
        // Spring AMQP trusts only java.lang/java.util by default, so our own payload
        // package must be whitelisted explicitly — and nothing else may be.
        typeMapper.setTrustedPackages("com.onlinejudge.common.dto", "java.util", "java.lang");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
