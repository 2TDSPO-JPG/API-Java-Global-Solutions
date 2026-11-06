package br.com.saneia.api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Modo sem broker (testes automatizados e execução rápida): o mesmo evento é entregue aos mesmos handlers
 * logo após o commit. Ative com SANEIA_MESSAGING_MODE=inline.
 */
@Component
@ConditionalOnProperty(name = "saneia.messaging.mode", havingValue = "inline")
public class InlineEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InlineEventPublisher.class);

    private final ObjectProvider<EventHandlers> handlers;

    public InlineEventPublisher(ObjectProvider<EventHandlers> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void publish(String routingKey, Object event) {
        AfterCommit.run(() -> {
            try {
                handlers.getObject().handle(event);
            } catch (RuntimeException ex) {
                log.error("Falha ao processar o evento {} no modo inline", routingKey, ex);
            }
        });
    }
}
