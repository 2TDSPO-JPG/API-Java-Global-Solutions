package br.com.saneia.api.sensor;

import com.fasterxml.jackson.annotation.JsonInclude;

/** { sensorId, status } do equipamento ativo mais próximo dentro do raio, ou ambos null. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CoverageResponse(String sensorId, SensorStatus status) {

    public static CoverageResponse none() {
        return new CoverageResponse(null, null);
    }
}
