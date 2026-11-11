package br.com.saneia.api.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import br.com.saneia.api.support.IntegrationTest;

/**
 * Provedor de IA configurado, porém fora do ar: a chamada real do Spring AI falha e a API responde com o
 * resumo calculado (aiGenerated=false), sem 500 e sem esperar as tentativas padrão do Spring AI.
 */
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=chave-de-teste-invalida",
        "spring.ai.openai.base-url=http://127.0.0.1:9",
        "spring.ai.retry.max-attempts=1"})
@DisplayName("Spring AI: provedor configurado mas indisponível cai no resumo calculado")
class InsightProviderIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("Falha do provedor: 200 com aiGenerated=false, resumo e indicadores calculados no backend")
    void providerFailureFallsBack() throws Exception {
        String id = read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("IA-01", -23.90, -46.90, 100))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        for (double volume : new double[] {400, 900, 1850}) {
            mvc.perform(post("/telemetry/readings").header("X-Device-Key", DEVICE_KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("sensorId", id, "volume", volume))))
                    .andExpect(status().isAccepted());
            clock.advance(Duration.ofMinutes(30));
        }
        long started = System.nanoTime();
        JsonNode insight = read(mvc.perform(post("/sensors/{id}/insights", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("period", "7d", "to", clock.instant().toString()))))
                .andExpect(status().isOk()).andReturn());
        assertThat(insight.get("aiGenerated").asBoolean()).isFalse();
        assertThat(insight.get("summary").asText()).isNotBlank();
        assertThat(insight.at("/indicators/readings").asInt()).isEqualTo(3);
        assertThat(insight.at("/indicators/maxVolume").asDouble()).isEqualTo(1850.0);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(15));
    }
}
