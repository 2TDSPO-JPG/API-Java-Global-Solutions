package br.com.saneia.api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Publica no exchange topic saneia.events depois do commit (o evento só sai se os dados foram gravados). */
@Component
@ConditionalOnProperty(name = "saneia.messaging.mode", havingValue = "rabbit", matchIfMissing = true)
public class RabbitEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventPublisher.class);

    private final RabbitTemplate rabbit;

    public RabbitEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    /**
     * Dentro de transação: publica após o commit e só registra a falha (os dados já foram gravados e o
     * cliente não deve repetir a operação). Fora de transação (ingestão IoT): a falha sobe como 503 para
     * o dispositivo reenviar a leitura.
     */
    @Override
    public void publish(String routingKey, Object event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            AfterCommit.run(() -> {
                try {
                    send(routingKey, event);
                } catch (AmqpException ex) {
                    log.error("Evento {} não publicado após o commit (RabbitMQ indisponível)", routingKey, ex);
                }
            });
        } else {
            send(routingKey, event);
        }
    }

    private void send(String routingKey, Object event) {
        rabbit.convertAndSend(Events.EXCHANGE, routingKey, event);
        log.debug("Evento {} publicado: {}", routingKey, event);
    }
}
