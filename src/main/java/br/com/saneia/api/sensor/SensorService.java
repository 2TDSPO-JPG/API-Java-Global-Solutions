package br.com.saneia.api.sensor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.saneia.api.common.ApiException;
import br.com.saneia.api.common.Coordinate;
import br.com.saneia.api.common.Geo;
import br.com.saneia.api.config.SaneiaProperties;

/** Catálogo de equipamentos, cobertura e montagem das respostas de monitoramento. */
@Service
public class SensorService {

    static final String SOURCE = "Sensor de nível (Wokwi via API)";
    private static final int LIST_READINGS = 12;
    private static final int LIST_EVENTS = 5;
    private static final int DETAIL_SAMPLES = 500;
    private static final int DETAIL_EVENTS = 30;
    private static final Duration DETAIL_HISTORY = Duration.ofDays(90);
    private static final Duration ALARM_HISTORY = Duration.ofDays(180);

    private final SensorRepository sensors;
    private final SensorReadingRepository readings;
    private final SensorEventRepository events;
    private final SaneiaProperties properties;
    private final Clock clock;

    public SensorService(SensorRepository sensors, SensorReadingRepository readings, SensorEventRepository events,
            SaneiaProperties properties, Clock clock) {
        this.sensors = sensors;
        this.readings = readings;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- consultas

    /** Catálogo ativo paginado (mapa da operadora e lista da administração). */
    @Transactional(readOnly = true)
    public Page<SensorResponse> listActive(Pageable pageable) {
        return sensors.findByActiveTrueOrderByPrefixAsc(pageable).map(sensor -> toResponse(sensor, false));
    }

    @Transactional(readOnly = true)
    public SensorResponse detail(Long id) {
        return toResponse(requireActive(id), true);
    }

    @Transactional(readOnly = true)
    public Sensor requireActive(Long id) {
        return sensors.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ApiException.notFound("Equipamento não encontrado."));
    }

    /** Equipamento ativo mais próximo cujo raio cobre o ponto (mesma regra do app). */
    @Transactional(readOnly = true)
    public Optional<Sensor> nearestCovering(double latitude, double longitude) {
        return sensors.findByActiveTrue().stream()
                .map(sensor -> new Candidate(sensor,
                        Geo.distanceMeters(latitude, longitude, sensor.getLatitude(), sensor.getLongitude())))
                .filter(candidate -> candidate.meters() <= candidate.sensor().getRadiusMeters())
                .min(Comparator.comparingDouble(Candidate::meters))
                .map(Candidate::sensor);
    }

    @Transactional(readOnly = true)
    public CoverageResponse coverage(double latitude, double longitude) {
        if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw ApiException.badRequest("Informe latitude entre -90 e 90 e longitude entre -180 e 180.");
        }
        return nearestCovering(latitude, longitude)
                .map(sensor -> new CoverageResponse(String.valueOf(sensor.getId()), effectiveStatus(sensor)))
                .orElseGet(CoverageResponse::none);
    }

    // ---------------------------------------------------------------- catálogo (admin)

    @Transactional
    public SensorResponse create(SensorInput input) {
        String prefix = normalizePrefix(input.prefix());
        validateCatalog(input);
        if (sensors.existsByPrefixIgnoreCaseAndActiveTrue(prefix)) {
            throw ApiException.conflict("Já existe um equipamento com esse prefixo.");
        }
        Instant now = clock.instant();
        Sensor sensor = new Sensor(now);
        apply(sensor, input, prefix, now);
        return toResponse(sensors.save(sensor), true);
    }

    @Transactional
    public SensorResponse update(Long id, SensorInput input) {
        Sensor sensor = requireActive(id);
        String prefix = normalizePrefix(input.prefix());
        validateCatalog(input);
        if (sensors.existsByPrefixIgnoreCaseAndActiveTrueAndIdNot(prefix, id)) {
            throw ApiException.conflict("Já existe um equipamento com esse prefixo.");
        }
        apply(sensor, input, prefix, clock.instant());
        return toResponse(sensor, true);
    }

    /** Retira do catálogo ativo, do mapa e da cobertura. Leituras, eventos e vínculos de protocolos são preservados. */
    @Transactional
    public void retire(Long id) {
        requireActive(id).retire(clock.instant());
    }

    private void apply(Sensor sensor, SensorInput input, String prefix, Instant now) {
        int interval = input.expectedIntervalMinutes() != null ? input.expectedIntervalMinutes()
                : sensor.getExpectedIntervalMinutes() > 0 ? sensor.getExpectedIntervalMinutes()
                : properties.telemetry().defaultExpectedIntervalMinutes();
        sensor.updateCatalog(prefix, input.name().trim(), input.equipmentType().trim(), input.address().trim(),
                input.neighborhood().trim(), input.coordinate().latitude(), input.coordinate().longitude(),
                input.capacity(), input.limit(), input.radiusMeters(), interval, now);
    }

    private static void validateCatalog(SensorInput input) {
        if (input.limit() > input.capacity()) {
            throw ApiException.badRequest("Informe uma capacidade positiva e um limite entre zero e a capacidade.");
        }
    }

    static String normalizePrefix(String prefix) {
        return prefix.trim().toUpperCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- mapeamento

    /** Considera "sem comunicação" quando passou do dobro do intervalo esperado sem leituras. */
    public SensorStatus effectiveStatus(Sensor sensor) {
        if (sensor.getLastReadingAt() == null) {
            return SensorStatus.OFFLINE;
        }
        return isStale(sensor, clock.instant()) ? SensorStatus.OFFLINE : sensor.getStatus();
    }

    public boolean isStale(Sensor sensor, Instant now) {
        if (sensor.getLastReadingAt() == null) {
            return true;
        }
        Duration tolerance = Duration.ofMinutes(Math.max(1, sensor.getExpectedIntervalMinutes()) * 2L);
        return sensor.getLastReadingAt().plus(tolerance).isBefore(now);
    }

    SensorResponse toResponse(Sensor sensor, boolean detail) {
        Instant now = clock.instant();
        SensorStatus status = effectiveStatus(sensor);
        int readingCount = detail ? DETAIL_SAMPLES : LIST_READINGS;
        List<SensorReading> recent = new ArrayList<>(detail
                ? readings.findBySensorIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDescIdDesc(
                        sensor.getId(), now.minus(DETAIL_HISTORY), PageRequest.of(0, readingCount))
                : readings.findBySensorIdOrderByMeasuredAtDescIdDesc(sensor.getId(), PageRequest.of(0, readingCount)));
        Collections.reverse(recent);

        List<Double> volumes = recent.stream()
                .skip(Math.max(0, recent.size() - LIST_READINGS))
                .map(SensorReading::getVolume)
                .toList();
        List<SensorResponse.Sample> samples = detail ? recent.stream().map(SensorService::toSample).toList() : null;
        List<SensorResponse.Event> eventList = events
                .findBySensorIdOrderByStartedAtDescIdDesc(sensor.getId(), PageRequest.of(0, detail ? DETAIL_EVENTS : LIST_EVENTS))
                .stream().map(SensorService::toEvent).toList();
        long alarms = events.countBySensorIdAndKindAndSeverityAndStartedAtGreaterThanEqual(
                sensor.getId(), EventKind.LEVEL, SensorStatus.ALARM, now.minus(ALARM_HISTORY));

        Double eta = status == SensorStatus.OFFLINE ? null : Forecast.hoursToLimit(
                recent.stream().map(r -> new Forecast.Point(r.getMeasuredAt(), r.getVolume())).toList(),
                sensor.getLimit());
        String reason = status == SensorStatus.OFFLINE && sensor.getStatus() != SensorStatus.OFFLINE
                && sensor.getLastReadingAt() != null
                ? "Sem leituras dentro do intervalo esperado." : sensor.getReason();

        return new SensorResponse(
                String.valueOf(sensor.getId()), sensor.getPrefix(), sensor.getName(), sensor.getEquipmentType(),
                sensor.getAddress(), sensor.getNeighborhood(),
                new Coordinate(sensor.getLatitude(), sensor.getLongitude()),
                status, sensor.getCapacity(), sensor.getLimit(), sensor.getVolume(), sensor.getLevelCm(),
                sensor.getLastReadingAt(), eta, alarms, volumes, samples, eventList, reason, null,
                SOURCE, sensor.getExpectedIntervalMinutes(), sensor.getRadiusMeters());
    }

    private static SensorResponse.Sample toSample(SensorReading reading) {
        return new SensorResponse.Sample(String.valueOf(reading.getId()), reading.getMeasuredAt(), reading.getVolume(),
                reading.getLevelCm(), reading.getStatus(), reading.getCapacity(), reading.getLimit(),
                reading.getSource(), reading.getNote());
    }

    private static SensorResponse.Event toEvent(SensorEvent event) {
        return new SensorResponse.Event(String.valueOf(event.getId()), event.getSeverity(),
                event.getKind().name().toLowerCase(Locale.ROOT), event.getStartedAt(), event.getEndedAt(),
                event.getMessage());
    }

    private record Candidate(Sensor sensor, double meters) {
    }
}
