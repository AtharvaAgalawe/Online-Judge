package com.onlinejudge.worker.config;

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
 * The worker declares the same topology as the backend (idempotent on the broker) so it
 * can run even when the API is down; names come from the shared contract in
 * {@link MessagingTopology}.
 */
@Configuration
public class RabbitConfig {

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

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        // Only the shared job-message package may be instantiated from the __TypeId__
        // header; Spring AMQP's safe default rejects everything else.
        typeMapper.setTrustedPackages("com.onlinejudge.common.dto", "java.util", "java.lang");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
