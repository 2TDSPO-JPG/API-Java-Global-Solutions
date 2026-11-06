package br.com.saneia.api.report;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Tipo do desfecho ao encerrar. STABILIZED = encerrado em observação: se o sensor vinculado voltar ao alarme
 * dentro da janela de observação, o chamado é reaberto automaticamente.
 */
public enum ReportOutcome {
    RESOLVED("resolved", "Resolvido"),
    STABILIZED("stabilized", "Estabilizado (em observação)"),
    UNFOUNDED("unfounded", "Sem procedência"),
    FORWARDED("forwarded", "Encaminhado a outro órgão");

    private final String apiValue;
    private final String label;

    ReportOutcome(String apiValue, String label) {
        this.apiValue = apiValue;
        this.label = label;
    }

    @JsonValue
    public String apiValue() {
        return apiValue;
    }

    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static ReportOutcome fromApi(String value) {
        for (ReportOutcome outcome : values()) {
            if (outcome.apiValue.equalsIgnoreCase(value) || outcome.name().equalsIgnoreCase(value)) {
                return outcome;
            }
        }
        throw new IllegalArgumentException("Desfecho inválido: " + value);
    }
}
