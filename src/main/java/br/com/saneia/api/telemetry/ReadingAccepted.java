package br.com.saneia.api.telemetry;

import java.time.Instant;

/** Resposta 202: a leitura foi validada e publicada na fila saneia.telemetry.readings. */
public record ReadingAccepted(String sensorId, String prefix, Instant measuredAt, String queue) {
}
