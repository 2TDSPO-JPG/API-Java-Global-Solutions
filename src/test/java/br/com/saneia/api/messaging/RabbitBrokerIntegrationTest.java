package br.com.saneia.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import br.com.saneia.api.support.IntegrationTest;

/**
 * Publicação e consumo com um RabbitMQ REAL (não roda no CI comum, que usa o modo inline).
 * Como executar (broker em localhost:5672, guest/guest):
 *   docker compose -f dockerfiles/docker-compose.yml up -d rabbitmq
 *   SANEIA_IT_RABBIT=true ./mvnw test -Dtest=RabbitBrokerIntegrationTest
 * Host e credenciais vêm de RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USER e RABBITMQ_PASSWORD.
 */
@EnabledIfEnvironmentVariable(named = "SANEIA_IT_RABBIT", matches = "true")
@TestPropertySource(properties = {
        "saneia.messaging.mode=rabbit",
        // o perfil de teste exclui o RabbitAutoConfiguration; aqui a lista é trocada por uma auto-config inofensiva
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration",
        "spring.rabbitmq.listener.simple.retry.initial-interval=200ms"})
@DisplayName("RabbitMQ real: publicação, chegada na fila, consumo pelo listener e DLQ")
class RabbitBrokerIntegrationTest extends IntegrationTest {

    @Autowired
    private AmqpAdmin amqp;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private RabbitListenerEndpointRegistry listeners;

    private static void await(String what, Duration timeout, BooleanSupplier condition) throws InterruptedException {
        Instant limit = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            assertThat(Instant.now()).as("aguardando: " + what).isBefore(limit);
            Thread.sleep(200);
        }
    }

    private int messages(String queue) {
        Properties info = amqp.getQueueProperties(queue);
        return info == null ? -1 : ((Number) info.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue();
    }

    private JsonNode sensor(String id) throws Exception {
        return read(mvc.perform(get("/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    @DisplayName("Leitura: API publica (202), a mensagem fica na fila com o consumidor parado e é gravada quando ele volta")
    void telemetryThroughBroker() throws Exception {
        String admin = bearer(ADMIN);
        String id = read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("RMQ-01", -23.80, -46.80, 100))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();

        listeners.getListenerContainer("telemetry-consumer").stop();
        amqp.purgeQueue(Events.TELEMETRY_QUEUE, false);

        mvc.perform(post("/telemetry/readings").header("X-Device-Key", DEVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("sensorId", id, "volume", 950))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.queue").value(Events.TELEMETRY_QUEUE));

        await("mensagem na fila " + Events.TELEMETRY_QUEUE, Duration.ofSeconds(10),
                () -> messages(Events.TELEMETRY_QUEUE) == 1);
        assertThat(sensor(id).get("volume").isNull()).as("nada gravado antes do consumo").isTrue();

        listeners.getListenerContainer("telemetry-consumer").start();
        await("consumo e gravação da leitura", Duration.ofSeconds(20), () -> {
            try {
                JsonNode current = sensor(id);
                return !current.get("volume").isNull() && current.get("volume").asDouble() == 950.0;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        assertThat(messages(Events.TELEMETRY_QUEUE)).isZero();
        assertThat(sensor(id).get("readings").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("Status do relato: report.status passa pela fila de notificações e vira notificação do morador")
    void notificationThroughBroker() throws Exception {
        String resident = "Bearer " + read(mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Morador Fila", "email", "fila@saneia.test", "password", "senhaForte1"))))
                .andExpect(status().isCreated()).andReturn()).get("token").asText();
        String reportId = read(mvc.perform(post("/reports").header(HttpHeaders.AUTHORIZATION, resident)
                        .contentType(MediaType.APPLICATION_JSON).content(body(reportInput("Vazamento", -23.81, -46.81))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        mvc.perform(patch("/reports/{id}/status", reportId).header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "in_progress", "progressNote", "Equipe a caminho."))))
                .andExpect(status().isOk());

        await("notificação do morador gerada pelo consumidor", Duration.ofSeconds(20), () -> {
            try {
                JsonNode list = read(mvc.perform(get("/notifications").header(HttpHeaders.AUTHORIZATION, resident))
                        .andReturn());
                return list.isArray() && list.size() > 0;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    @Test
    @DisplayName("Mensagem inválida: rejeitada sem reprocessar em loop e enviada para a DLQ saneia.events.dlq")
    void invalidMessageGoesToDlq() throws Exception {
        amqp.purgeQueue(Events.DEAD_LETTER_QUEUE, false);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(Events.EXCHANGE, Events.KEY_READING, new Message("{ isto não é json".getBytes(), properties));
        await("mensagem na DLQ", Duration.ofSeconds(20), () -> messages(Events.DEAD_LETTER_QUEUE) == 1);
        assertThat(messages(Events.TELEMETRY_QUEUE)).isZero();
    }
}
