package br.com.saneia.api.sensor;

import com.fasterxml.jackson.annotation.JsonValue;

/** Estados do contrato: normal, warning (alerta), alarm (limite atingido) e offline (sem comunicação). */
public enum SensorStatus {
    NORMAL("normal"),
    WARNING("warning"),
    ALARM("alarm"),
    OFFLINE("offline");

    private final String apiValue;

    SensorStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String apiValue() {
        return apiValue;
    }

    public boolean isAlert() {
        return this == WARNING || this == ALARM;
    }
}
