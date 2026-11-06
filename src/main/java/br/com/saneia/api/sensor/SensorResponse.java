package br.com.saneia.api.sensor;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.saneia.api.common.Coordinate;

/** Sensor do contrato do app (mapa, ficha e catálogo administrativo). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SensorResponse(
        String id,
        String prefix,
        String name,
        String equipmentType,
        String address,
        String neighborhood,
        Coordinate coordinate,
        @JsonInclude(JsonInclude.Include.ALWAYS) SensorStatus status,
        double capacity,
        double limit,
        @JsonInclude(JsonInclude.Include.ALWAYS) Double volume,
        Double levelCm,
        @JsonInclude(JsonInclude.Include.ALWAYS) Instant lastReadingAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) Double etaHours,
        long alarmsSixMonths,
        @JsonInclude(JsonInclude.Include.ALWAYS) List<Double> readings,
        List<Sample> samples,
        List<Event> events,
        String reason,
        String summary,
        String source,
        int expectedIntervalMinutes,
        double radiusMeters) {

    /** Uma amostra da série histórica (abre o detalhe de cada barra no app). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Sample(String id, Instant at, double volume, Double levelCm, SensorStatus status,
            double capacity, double limit, String source, String note) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Event(String id, SensorStatus status, String kind, Instant at, Instant endedAt, String message) {
    }
}
