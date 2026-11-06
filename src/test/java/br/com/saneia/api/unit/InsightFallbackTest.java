package br.com.saneia.api.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.saneia.api.insight.InsightIndicators;
import br.com.saneia.api.insight.InsightServiceTestAccess;

@DisplayName("Resumo calculado quando o LLM não está disponível")
class InsightFallbackTest {

    @Test
    @DisplayName("Usa somente os números calculados pelo backend")
    void fallbackUsesIndicators() {
        InsightIndicators indicators = new InsightIndicators("1", "RES-07", "Reservatório 07", "7d",
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-08T00:00:00Z"), 12,
                1200d, 1650d, 1100d, 1700d, Instant.parse("2026-11-07T18:00:00Z"), 1450d, 450d,
                2000, 1800, 0, 0, 1, null, 0, null, 8.0, "warning", Instant.parse("2026-11-07T23:30:00Z"));
        String text = InsightServiceTestAccess.fallback(indicators);
        assertThat(text).contains("12 leituras").contains("alta de 450 L").contains("1700 L")
                .contains("Não houve alarme").contains("cerca de 8,0 horas");
    }
}
