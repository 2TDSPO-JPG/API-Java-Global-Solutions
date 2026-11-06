package br.com.saneia.api.telemetry;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.saneia.api.common.ApiException;
import br.com.saneia.api.config.SaneiaProperties;
import br.com.saneia.api.messaging.EventPublisher;
import br.com.saneia.api.messaging.Events;
import br.com.saneia.api.sensor.Sensor;
import br.com.saneia.api.sensor.SensorRepository;

/** Valida a leitura do dispositivo e publica o evento; a gravação e a avaliação acontecem no consumidor. */
@Service
public class TelemetryService {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final SensorRepository sensors;
    private final EventPublisher publisher;
    private final SaneiaProperties properties;
    private final Clock clock;

    public TelemetryService(SensorRepository sensors, EventPublisher publisher, SaneiaProperties properties, Clock clock) {
        this.sensors = sensors;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    public void checkDeviceKey(String provided) {
        String expected = properties.telemetry().deviceKey();
        if (!StringUtils.hasText(expected)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A ingestão de leituras está desativada: defina DEVICE_KEY no servidor.");
        }
        if (!StringUtils.hasText(provided) || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Chave do dispositivo inválida.");
        }
    }

    /** Sem transação: a publicação é imediata e uma falha do broker volta como 503 ao dispositivo. */
    public ReadingAccepted accept(ReadingRequest request) {
        Sensor sensor = resolve(request);
        Instant now = clock.instant();
        Instant measuredAt = request.measuredAt() == null ? now : request.measuredAt();
        if (measuredAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            throw ApiException.unprocessable("A data da leitura está no futuro.");
        }
        if (request.volume() > sensor.getCapacity()) {
            throw ApiException.unprocessable("O volume informado é maior que a capacidade do equipamento.");
        }
        String source = StringUtils.hasText(request.source()) ? request.source().trim() : "Wokwi";
        publisher.publish(Events.KEY_READING, new Events.ReadingReceived(sensor.getId(), request.volume(),
                request.levelCm(), measuredAt, now, source, request.note()));
        return new ReadingAccepted(String.valueOf(sensor.getId()), sensor.getPrefix(), measuredAt, Events.TELEMETRY_QUEUE);
    }

    private Sensor resolve(ReadingRequest request) {
        if (StringUtils.hasText(request.sensorId())) {
            Long id;
            try {
                id = Long.valueOf(request.sensorId().trim());
            } catch (NumberFormatException ex) {
                throw ApiException.notFound("Equipamento não encontrado.");
            }
            return sensors.findByIdAndActiveTrue(id)
                    .orElseThrow(() -> ApiException.notFound("Equipamento não encontrado."));
        }
        if (StringUtils.hasText(request.prefix())) {
            return sensors.findFirstByPrefixIgnoreCaseAndActiveTrue(request.prefix().trim())
                    .orElseThrow(() -> ApiException.notFound("Equipamento não encontrado."));
        }
        throw ApiException.badRequest("Informe sensorId ou prefix do equipamento.");
    }
}
