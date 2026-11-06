package br.com.saneia.api.report;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Fluxo do protocolo: received → in_progress → closed. Independente do estado do sensor. */
public enum ReportStatus {
    RECEIVED("received"),
    IN_PROGRESS("in_progress"),
    CLOSED("closed");

    private final String apiValue;

    ReportStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String apiValue() {
        return apiValue;
    }

    @JsonCreator
    public static ReportStatus fromApi(String value) {
        for (ReportStatus status : values()) {
            if (status.apiValue.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status inválido: " + value);
    }

    /** Transições permitidas: manter o mesmo status (só nota) ou avançar uma etapa. */
    public boolean canMoveTo(ReportStatus next) {
        return next == this || (this == RECEIVED && next == IN_PROGRESS) || (this == IN_PROGRESS && next == CLOSED);
    }
}
