package com.onlinejudge.backend.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import com.onlinejudge.common.dto.SubmissionJobMessage;

/**
 * Regression guard for the Spring AMQP trusted-packages default: the converter must be
 * able to deserialize our own payload class from the {@code __TypeId__} header, or every
 * job message receive fails at runtime (this broke CI before the fix).
 */
class JobMessageConverterTest {

    private final Jackson2JsonMessageConverter converter = new RabbitTopologyConfig().jsonMessageConverter();

    @Test
    void roundTripsSubmissionJobMessage() {
        SubmissionJobMessage original = new SubmissionJobMessage(42L, 1L, 2, 1_500, 65_536, List.of(11L, 12L));

        Message message = converter.toMessage(original, new MessageProperties());
        Object deserialized = converter.fromMessage(message);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void rejectsClassesOutsideTheTrustedPackages() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", "java.io.File");
        Message message = new Message("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), properties);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> converter.fromMessage(message)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in the trusted packages");
    }
}
