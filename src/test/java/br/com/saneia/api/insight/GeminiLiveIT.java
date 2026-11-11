package br.com.saneia.api.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import br.com.saneia.api.support.IntegrationTest;

/**
 * Chamada REAL ao Gemini via Spring AI (só roda com a chave no ambiente; o CI comum pula):
 *   GEMINI_API_KEY=... ./mvnw test -Dtest=GeminiLiveIT
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@TestPropertySource(properties = "spring.ai.openai.api-key=${GEMINI_API_KEY}")
@DisplayName("Spring AI + Gemini real: resumo gerado pelo LLM a partir dos indicadores do backend")
class GeminiLiveIT extends IntegrationTest {

    @Test
    @DisplayName("aiGenerated=true, modelo informado e indicadores iguais aos calculados")
    void realGemini() throws Exception {
        String id = read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("GEM-01", -23.91, -46.91, 100))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        for (double volume : new double[] {600, 1200, 1700, 1850}) {
            mvc.perform(post("/telemetry/readings").header("X-Device-Key", DEVICE_KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("sensorId", id, "volume", volume))))
                    .andExpect(status().isAccepted());
            clock.advance(Duration.ofMinutes(30));
        }
        JsonNode insight = read(mvc.perform(post("/sensors/{id}/insights", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("period", "7d", "to", clock.instant().toString()))))
                .andExpect(status().isOk()).andReturn());
        System.out.println("Resposta do Gemini: " + insight.toPrettyString());
        assertThat(insight.get("aiGenerated").asBoolean()).as("texto veio do LLM").isTrue();
        assertThat(insight.get("model").asText()).isNotBlank();
        assertThat(insight.get("summary").asText()).hasSizeGreaterThan(40);
        assertThat(insight.at("/indicators/readings").asInt()).isEqualTo(4);
        assertThat(insight.at("/indicators/maxVolume").asDouble()).isEqualTo(1850.0);
    }
}
