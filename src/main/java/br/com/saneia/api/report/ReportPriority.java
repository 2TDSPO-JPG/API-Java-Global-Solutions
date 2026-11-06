package br.com.saneia.api.report;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Prioridade do chamado com o prazo de atendimento (SLA) correspondente. */
public enum ReportPriority {
    LOW("low", "Baixa", Duration.ofDays(7)),
    MEDIUM("medium", "Média", Duration.ofHours(72)),
    HIGH("high", "Alta", Duration.ofHours(24)),
    CRITICAL("critical", "Crítica", Duration.ofHours(4));

    private final String apiValue;
    private final String label;
    private final Duration sla;

    ReportPriority(String apiValue, String label, Duration sla) {
        this.apiValue = apiValue;
        this.label = label;
        this.sla = sla;
    }

    @JsonValue
    public String apiValue() {
        return apiValue;
    }

    public String getLabel() {
        return label;
    }

    public Duration sla() {
        return sla;
    }

    @JsonCreator
    public static ReportPriority fromApi(String value) {
        for (ReportPriority priority : values()) {
            if (priority.apiValue.equalsIgnoreCase(value) || priority.name().equalsIgnoreCase(value)) {
                return priority;
            }
        }
        throw new IllegalArgumentException("Prioridade inválida: " + value);
    }
}
