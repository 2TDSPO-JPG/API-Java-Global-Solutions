package br.com.saneia.api.insight;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Indicadores calculados pelo backend a partir das medições e eventos gravados. São a fonte dos números. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InsightIndicators(
        String sensorId,
        String prefix,
        String name,
        String period,
        Instant from,
        Instant to,
        int readings,
        Double firstVolume,
        Double lastVolume,
        Double minVolume,
        Double maxVolume,
        Instant maxVolumeAt,
        Double averageVolume,
        Double changeLiters,
        double capacity,
        double limit,
        int readingsAtOrAboveLimit,
        int alarmEvents,
        int warningEvents,
        Double longestAlarmHours,
        int offlineEvents,
        Double offlineHours,
        Double etaHours,
        String currentStatus,
        Instant lastReadingAt) {
}
