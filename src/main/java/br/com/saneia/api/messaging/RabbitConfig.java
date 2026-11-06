package br.com.saneia.api.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Topologia: exchange topic saneia.events →
 * fila saneia.telemetry.readings (telemetry.reading) e fila saneia.notifications (report.# e sensor.#).
 * Mensagens rejeitadas após as tentativas vão para a DLQ saneia.events.dlq.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public Declarables saneiaTopology() {
        TopicExchange exchange = new TopicExchange(Events.EXCHANGE, true, false);
        TopicExchange deadLetters = new TopicExchange(Events.DEAD_LETTER_EXCHANGE, true, false);
        Queue telemetry = QueueBuilder.durable(Events.TELEMETRY_QUEUE)
                .deadLetterExchange(Events.DEAD_LETTER_EXCHANGE).build();
        Queue notifications = QueueBuilder.durable(Events.NOTIFICATION_QUEUE)
                .deadLetterExchange(Events.DEAD_LETTER_EXCHANGE).build();
        Queue dlq = QueueBuilder.durable(Events.DEAD_LETTER_QUEUE).build();
        Binding telemetryBinding = BindingBuilder.bind(telemetry).to(exchange).with(Events.KEY_READING);
        Binding reportBinding = BindingBuilder.bind(notifications).to(exchange).with("report.#");
        Binding sensorBinding = BindingBuilder.bind(notifications).to(exchange).with("sensor.#");
        Binding dlqBinding = BindingBuilder.bind(dlq).to(deadLetters).with("#");
        return new Declarables(exchange, deadLetters, telemetry, notifications, dlq,
                telemetryBinding, reportBinding, sensorBinding, dlqBinding);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("br.com.saneia.api.messaging");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
