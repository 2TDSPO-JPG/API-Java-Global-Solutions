package br.com.saneia.api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Consumidores assíncronos das filas do Saneia. */
@Component
@ConditionalOnProperty(name = "saneia.messaging.mode", havingValue = "rabbit", matchIfMissing = true)
public class RabbitListeners {

    private static final Logger log = LoggerFactory.getLogger(RabbitListeners.class);

    private final EventHandlers handlers;

    public RabbitListeners(EventHandlers handlers) {
        this.handlers = handlers;
    }

    @RabbitListener(queues = Events.TELEMETRY_QUEUE, id = "telemetry-consumer")
    public void onReading(Events.ReadingReceived reading) {
        log.info("Leitura consumida da fila: sensor={} volume={}", reading.sensorId(), reading.volume());
        handlers.handle(reading);
    }

    @Component
    @ConditionalOnProperty(name = "saneia.messaging.mode", havingValue = "rabbit", matchIfMissing = true)
    @RabbitListener(queues = Events.NOTIFICATION_QUEUE, id = "notification-consumer")
    public static class NotificationConsumer {

        private final EventHandlers handlers;

        public NotificationConsumer(EventHandlers handlers) {
            this.handlers = handlers;
        }

        @RabbitHandler
        public void onSensorAlert(Events.SensorAlert event) {
            handlers.handle(event);
        }

        @RabbitHandler
        public void onSensorNormalized(Events.SensorNormalized event) {
            handlers.handle(event);
        }

        @RabbitHandler
        public void onReportOpened(Events.ReportOpened event) {
            handlers.handle(event);
        }

        @RabbitHandler
        public void onReportStatus(Events.ReportStatusChanged event) {
            handlers.handle(event);
        }

        @RabbitHandler
        public void onAssessment(Events.AssessmentRequested event) {
            handlers.handle(event);
        }
    }
}
