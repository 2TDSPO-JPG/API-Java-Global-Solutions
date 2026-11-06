package br.com.saneia.api.messaging;

/** Publica eventos de domínio somente após o commit da transação atual (ou na hora, se não houver transação). */
public interface EventPublisher {

    void publish(String routingKey, Object event);
}
