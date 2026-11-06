package br.com.saneia.api.insight;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Período da análise: today, 7d, 30d, 90d ou all. Limites inclusivos em ISO 8601 (from omitido em all). */
public record InsightRequest(
        @NotBlank(message = "Informe o período.")
        @Pattern(regexp = "today|7d|30d|90d|all", message = "Período inválido. Use today, 7d, 30d, 90d ou all.")
        String period,
        Instant from,
        Instant to) {
}
