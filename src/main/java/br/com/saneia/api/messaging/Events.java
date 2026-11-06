package br.com.saneia.api.messaging;

import java.time.Instant;

/** Eventos de domínio trafegados como JSON pelo RabbitMQ (exchange saneia.events). */
public final class Events {

    public static final String EXCHANGE = "saneia.events";
    public static final String DEAD_LETTER_EXCHANGE = "saneia.events.dlx";
    public static final String TELEMETRY_QUEUE = "saneia.telemetry.readings";
    public static final String NOTIFICATION_QUEUE = "saneia.notifications";
    public static final String DEAD_LETTER_QUEUE = "saneia.events.dlq";

    public static final String KEY_READING = "telemetry.reading";
    public static final String KEY_SENSOR_ALERT = "sensor.alert";
    public static final String KEY_SENSOR_NORMALIZED = "sensor.normalized";
    public static final String KEY_REPORT_OPENED = "report.opened";
    public static final String KEY_REPORT_STATUS = "report.status";
    public static final String KEY_REPORT_ASSESSMENT = "report.assessment";

    private Events() {
    }

    /** Leitura aceita pela API e enfileirada para gravação e avaliação de prioridade. */
    public record ReadingReceived(Long sensorId, double volume, Double levelCm, Instant measuredAt,
            Instant receivedAt, String source, String note) {
    }

    /** Alerta prioritário aberto ou agravado (nível ou perda de comunicação). */
    public record SensorAlert(Long sensorId, String prefix, String name, String status, String message, Instant at) {
    }

    public record SensorNormalized(Long sensorId, String prefix, String name, Instant at) {
    }

    public record ReportOpened(Long reportId, String protocol, Long ownerId, String category, String neighborhood,
            boolean recurrent, Instant at) {
    }

    public record ReportStatusChanged(Long reportId, String protocol, Long ownerId, String status, String resolution,
            String progressNote, Instant at) {
    }

    public record AssessmentRequested(Long reportId, String protocol, String note, Instant at) {
    }
}
