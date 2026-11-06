package br.com.saneia.api.telemetry;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.saneia.api.config.SaneiaProperties;
import br.com.saneia.api.messaging.EventPublisher;
import br.com.saneia.api.messaging.Events;
import br.com.saneia.api.sensor.EventKind;
import br.com.saneia.api.sensor.Forecast;
import br.com.saneia.api.sensor.Sensor;
import br.com.saneia.api.sensor.SensorEvent;
import br.com.saneia.api.sensor.SensorEventRepository;
import br.com.saneia.api.sensor.SensorReading;
import br.com.saneia.api.sensor.SensorReadingRepository;
import br.com.saneia.api.sensor.SensorRepository;
import br.com.saneia.api.sensor.SensorService;
import br.com.saneia.api.sensor.SensorStatus;

/**
 * Consumidor das leituras: grava o histórico, avalia o limite crítico e a previsão, abre/agrava/encerra
 * eventos e sinaliza perda de comunicação. Regras do guia do projeto (páginas 05 a 07).
 */
@Service
public class TelemetryProcessor {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProcessor.class);
    private static final int FORECAST_READINGS = 20;

    private final SensorRepository sensors;
    private final SensorReadingRepository readings;
    private final SensorEventRepository events;
    private final SensorService sensorService;
    private final EventPublisher publisher;
    private final SaneiaProperties properties;
    private final Clock clock;

    public TelemetryProcessor(SensorRepository sensors, SensorReadingRepository readings, SensorEventRepository events,
            SensorService sensorService, EventPublisher publisher, SaneiaProperties properties, Clock clock) {
        this.sensors = sensors;
        this.readings = readings;
        this.events = events;
        this.sensorService = sensorService;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    /** Resultado da avaliação de uma leitura (estado instantâneo e motivo). */
    record Evaluation(SensorStatus status, String reason, Double etaHours) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Events.ReadingReceived message) {
        Optional<Sensor> found = sensors.lockById(message.sensorId());
        if (found.isEmpty() || !found.get().isActive()) {
            log.warn("Leitura descartada: equipamento {} inexistente ou retirado do catálogo", message.sensorId());
            return;
        }
        Sensor sensor = found.get();
        Instant now = clock.instant();

        List<Forecast.Point> history = new ArrayList<>(readings
                .findBySensorIdOrderByMeasuredAtDescIdDesc(sensor.getId(), PageRequest.of(0, FORECAST_READINGS))
                .stream().map(r -> new Forecast.Point(r.getMeasuredAt(), r.getVolume())).toList());
        Collections.reverse(history);
        history.add(new Forecast.Point(message.measuredAt(), message.volume()));
        history.sort((a, b) -> a.at().compareTo(b.at()));

        Evaluation evaluation = evaluate(sensor, message.volume(), history);
        readings.save(new SensorReading(sensor, message.measuredAt(), message.receivedAt(), message.volume(),
                message.levelCm(), evaluation.status(), message.source(), message.note()));

        boolean newest = sensor.getLastReadingAt() == null || !message.measuredAt().isBefore(sensor.getLastReadingAt());
        if (!newest) {
            // Leitura atrasada: entra no histórico, mas não muda o estado atual do ponto.
            return;
        }
        boolean wasOffline = sensor.getStatus() == SensorStatus.OFFLINE && sensor.getLastReadingAt() != null;
        sensor.applyReading(message.volume(), message.levelCm(), message.measuredAt());
        closeCommunicationEvent(sensor, message.measuredAt(), wasOffline);
        applyLevelRules(sensor, evaluation, message, now);
    }

    Evaluation evaluate(Sensor sensor, double volume, List<Forecast.Point> history) {
        SaneiaProperties.Telemetry config = properties.telemetry();
        if (volume >= sensor.getLimit()) {
            return new Evaluation(SensorStatus.ALARM, String.format(Locale.ROOT,
                    "Limite operacional atingido: %.0f L de %.0f L.", volume, sensor.getLimit()), null);
        }
        Double eta = Forecast.hoursToLimit(history, sensor.getLimit());
        if (eta != null && eta <= config.forecastWindowHours()) {
            return new Evaluation(SensorStatus.WARNING, String.format(Locale.ROOT,
                    "Previsão de atingir o limite em cerca de %s horas.", formatHours(eta)), eta);
        }
        if (volume >= sensor.getLimit() * config.warningRatio()) {
            return new Evaluation(SensorStatus.WARNING, String.format(Locale.ROOT,
                    "Volume acima de %.0f%% do limite operacional.", config.warningRatio() * 100), eta);
        }
        return new Evaluation(SensorStatus.NORMAL, "Nível dentro da faixa normal.", eta);
    }

    private void applyLevelRules(Sensor sensor, Evaluation evaluation, Events.ReadingReceived message, Instant now) {
        Optional<SensorEvent> open = events
                .findFirstBySensorIdAndKindAndEndedAtIsNullOrderByStartedAtDesc(sensor.getId(), EventKind.LEVEL);
        if (evaluation.status().isAlert()) {
            sensor.setNormalStreak(0);
            if (open.isEmpty()) {
                SensorEvent event = new SensorEvent(sensor, EventKind.LEVEL, evaluation.status(),
                        message.measuredAt(), evaluation.reason(), message.volume());
                events.save(event);
                publishAlert(sensor, evaluation, message.measuredAt());
            } else {
                SensorEvent event = open.get();
                event.registerVolume(message.volume());
                if (event.getSeverity() == SensorStatus.WARNING && evaluation.status() == SensorStatus.ALARM) {
                    event.escalate(SensorStatus.ALARM, evaluation.reason());
                    publishAlert(sensor, evaluation, message.measuredAt());
                }
            }
            sensor.applyState(evaluation.status(), evaluation.reason(), now);
            return;
        }

        // Leitura normal: encerra o evento só após leituras estáveis consecutivas.
        if (open.isPresent()) {
            int streak = sensor.getNormalStreak() + 1;
            int required = Math.max(1, properties.telemetry().stableReadingsToNormalize());
            if (streak >= required) {
                open.get().close(message.measuredAt());
                sensor.setNormalStreak(0);
                sensor.applyState(SensorStatus.NORMAL, "Nível normalizado após leituras estáveis.", now);
                publisher.publish(Events.KEY_SENSOR_NORMALIZED, new Events.SensorNormalized(
                        sensor.getId(), sensor.getPrefix(), sensor.getName(), message.measuredAt()));
            } else {
                sensor.setNormalStreak(streak);
                sensor.applyState(SensorStatus.WARNING,
                        "Nível em queda; aguardando leituras estáveis para normalizar.", now);
            }
        } else {
            sensor.setNormalStreak(0);
            sensor.applyState(SensorStatus.NORMAL, evaluation.reason(), now);
        }
    }

    private void closeCommunicationEvent(Sensor sensor, Instant at, boolean wasOffline) {
        events.findFirstBySensorIdAndKindAndEndedAtIsNullOrderByStartedAtDesc(sensor.getId(), EventKind.COMMUNICATION)
                .ifPresent(event -> {
                    event.close(at);
                    log.info("Comunicação restabelecida com {} (estava offline: {})", sensor.getPrefix(), wasOffline);
                });
    }

    private void publishAlert(Sensor sensor, Evaluation evaluation, Instant at) {
        publisher.publish(Events.KEY_SENSOR_ALERT, new Events.SensorAlert(sensor.getId(), sensor.getPrefix(),
                sensor.getName(), evaluation.status().apiValue(), evaluation.reason(), at));
    }

    /** Marca como "Sem comunicação" os pontos que passaram do intervalo esperado sem leituras. */
    @Scheduled(fixedDelayString = "${saneia.telemetry.offline-check:PT1M}", initialDelayString = "PT30S")
    @Transactional
    public void detectOffline() {
        Instant now = clock.instant();
        for (Sensor sensor : sensors.findByActiveTrueAndLastReadingAtIsNotNullAndStatusNot(SensorStatus.OFFLINE)) {
            if (!sensorService.isStale(sensor, now)) {
                continue;
            }
            String message = "Sem comunicação desde a última leitura; verificar o dispositivo.";
            sensor.setNormalStreak(0);
            sensor.applyState(SensorStatus.OFFLINE, message, now);
            if (events.findFirstBySensorIdAndKindAndEndedAtIsNullOrderByStartedAtDesc(sensor.getId(),
                    EventKind.COMMUNICATION).isEmpty()) {
                events.save(new SensorEvent(sensor, EventKind.COMMUNICATION, SensorStatus.OFFLINE,
                        sensor.getLastReadingAt(), message, null));
            }
            publisher.publish(Events.KEY_SENSOR_ALERT, new Events.SensorAlert(sensor.getId(), sensor.getPrefix(),
                    sensor.getName(), SensorStatus.OFFLINE.apiValue(), message, now));
        }
    }

    private static String formatHours(double hours) {
        return hours >= 10 ? String.valueOf(Math.round(hours)) : String.format(Locale.ROOT, "%.1f", hours).replace('.', ',');
    }
}
