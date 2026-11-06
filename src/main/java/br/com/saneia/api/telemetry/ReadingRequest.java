package br.com.saneia.api.telemetry;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Leitura enviada pelo dispositivo (Wokwi → HiveMQ → ponte HTTP, ou direto).
 * Identifique o equipamento por sensorId (id da API) ou por prefix (ex.: RES-07).
 */
public record ReadingRequest(
        @Size(max = 40) String sensorId,
        @Size(max = 30) String prefix,
        @NotNull(message = "Informe o volume medido em litros.")
        @PositiveOrZero(message = "O volume não pode ser negativo.")
        Double volume,
        @PositiveOrZero(message = "O nível não pode ser negativo.") Double levelCm,
        Instant measuredAt,
        @Size(max = 60) String source,
        @Size(max = 255) String note) {
}
