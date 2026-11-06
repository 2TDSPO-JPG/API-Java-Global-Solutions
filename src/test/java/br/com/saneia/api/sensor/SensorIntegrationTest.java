package br.com.saneia.api.sensor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import br.com.saneia.api.notification.NotificationRepository;
import br.com.saneia.api.support.IntegrationTest;
import br.com.saneia.api.telemetry.TelemetryProcessor;

@DisplayName("Catálogo, cobertura, telemetria assíncrona e insights")
class SensorIntegrationTest extends IntegrationTest {

    @Autowired
    private TelemetryProcessor telemetryProcessor;

    @Autowired
    private NotificationRepository notifications;

    private String createSensor(String admin, String prefix, double lat, double lon, double radius) throws Exception {
        return read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput(prefix, lat, lon, radius))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private void sendReading(String sensorId, double volume) throws Exception {
        mvc.perform(post("/telemetry/readings").header("X-Device-Key", DEVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("sensorId", sensorId, "volume", volume))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.queue").value("saneia.telemetry.readings"));
    }

    @Test
    @DisplayName("CRUD do administrador: cria offline, valida, edita e retira do catálogo")
    void adminCrud() throws Exception {
        String admin = bearer(ADMIN);
        JsonNode created = read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("res-21", -23.56, -46.64, 200))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prefix").value("RES-21"))
                .andExpect(jsonPath("$.status").value("offline"))
                .andExpect(jsonPath("$.volume").isEmpty())
                .andExpect(jsonPath("$.lastReadingAt").isEmpty())
                .andExpect(jsonPath("$.readings").isEmpty())
                .andReturn());
        String id = created.get("id").asText();

        // prefixo único sem distinção de maiúsculas/minúsculas
        mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("RES-21", -23.0, -46.0, 100))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um equipamento com esse prefixo."));

        // limite maior que a capacidade
        Map<String, Object> invalid = new HashMap<>(sensorInput("RES-22", -23.0, -46.0, 100));
        invalid.put("limit", 5000);
        mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(invalid)))
                .andExpect(status().isBadRequest());

        // coordenada fora do intervalo (Bean Validation)
        Map<String, Object> badCoordinate = new HashMap<>(sensorInput("RES-23", -23.0, -46.0, 100));
        badCoordinate.put("coordinate", Map.of("latitude", 120, "longitude", 10));
        mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(badCoordinate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> edit = new HashMap<>(sensorInput("RES-21", -23.56, -46.64, 350));
        edit.put("name", "Reservatório editado");
        mvc.perform(patch("/admin/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reservatório editado"))
                .andExpect(jsonPath("$.radiusMeters").value(350.0));

        mvc.perform(get("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").exists());

        mvc.perform(delete("/admin/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cobertura usa o equipamento ativo mais próximo dentro do raio e some após a exclusão")
    void coverage() throws Exception {
        String admin = bearer(ADMIN);
        String near = createSensor(admin, "COV-01", -23.6000, -46.7000, 300);
        createSensor(admin, "COV-02", -23.6020, -46.7000, 300); // ~222 m de distância

        String resident = bearer(RESIDENT);
        mvc.perform(get("/coverage").param("latitude", "-23.6001").param("longitude", "-46.7000")
                        .header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorId").value(near))
                .andExpect(jsonPath("$.status").value("offline"));

        mvc.perform(get("/coverage").param("latitude", "-23.7000").param("longitude", "-46.9000")
                        .header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorId").isEmpty())
                .andExpect(jsonPath("$.status").isEmpty());
    }

    @Test
    @DisplayName("Leituras pela fila: alarme abre evento, alerta a operadora e normaliza após leituras estáveis")
    void telemetryFlow() throws Exception {
        String admin = bearer(ADMIN);
        String id = createSensor(admin, "TEL-01", -23.65, -46.75, 200);
        String operator = bearer(OPERATOR);

        sendReading(id, 900);
        mvc.perform(get("/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("normal"))
                .andExpect(jsonPath("$.volume").value(900.0))
                .andExpect(jsonPath("$.samples.length()").value(1));

        long before = notifications.count();
        clock.advance(Duration.ofMinutes(5));
        sendReading(id, 1850);
        mvc.perform(get("/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(jsonPath("$.status").value("alarm"))
                .andExpect(jsonPath("$.alarmsSixMonths").value(1))
                .andExpect(jsonPath("$.events[0].status").value("alarm"));
        assertThat(notifications.count()).isGreaterThan(before);

        // Leitura sucessiva da mesma condição não conta outro alarme
        clock.advance(Duration.ofMinutes(5));
        sendReading(id, 1870);
        clock.advance(Duration.ofMinutes(5));
        sendReading(id, 700);
        mvc.perform(get("/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(jsonPath("$.status").value("warning"))
                .andExpect(jsonPath("$.alarmsSixMonths").value(1));

        clock.advance(Duration.ofMinutes(5));
        sendReading(id, 650);
        mvc.perform(get("/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(jsonPath("$.status").value("normal"))
                .andExpect(jsonPath("$.alarmsSixMonths").value(1))
                .andExpect(jsonPath("$.readings.length()").value(5));

        // Sem leituras além do dobro do intervalo esperado: "Sem comunicação"
        clock.advance(Duration.ofHours(2));
        telemetryProcessor.detectOffline();
        mvc.perform(get("/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(jsonPath("$.status").value("offline"))
                .andExpect(jsonPath("$.events[0].kind").value("communication"));
    }

    @Test
    @DisplayName("Previsão: subida constante gera alerta com estimativa até o limite")
    void forecastWarning() throws Exception {
        String id = createSensor(bearer(ADMIN), "TEL-02", -23.66, -46.76, 200);
        double[] volumes = {1000, 1100, 1200, 1300};
        for (int i = 0; i < volumes.length; i++) {
            if (i > 0) {
                clock.advance(Duration.ofMinutes(20));
            }
            sendReading(id, volumes[i]);
        }
        // ritmo de 300 L/h: (1800 - 1300) / 300 = 1,7 h até o limite
        mvc.perform(get("/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(jsonPath("$.status").value("warning"))
                .andExpect(jsonPath("$.etaHours").value(1.7));
    }

    @Test
    @DisplayName("Telemetria exige X-Device-Key válida e volume dentro da capacidade")
    void telemetryValidation() throws Exception {
        String id = createSensor(bearer(ADMIN), "TEL-03", -23.67, -46.77, 200);
        mvc.perform(post("/telemetry/readings").header("X-Device-Key", "errada")
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("sensorId", id, "volume", 10))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/telemetry/readings").header("X-Device-Key", DEVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("prefix", "tel-03", "volume", 9999))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/telemetry/readings").header("X-Device-Key", DEVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("prefix", "TEL-03", "volume", 120))))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("Insights: indicadores calculados no backend e resumo (fallback sem chave de IA)")
    void insights() throws Exception {
        String id = createSensor(bearer(ADMIN), "INS-01", -23.68, -46.78, 200);
        sendReading(id, 500);
        clock.advance(Duration.ofMinutes(30));
        sendReading(id, 1820);
        String operator = bearer(OPERATOR);
        mvc.perform(post("/sensors/{id}/insights", id).header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("period", "7d", "to", clock.instant().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.aiGenerated").value(false))
                .andExpect(jsonPath("$.indicators.readings").value(2))
                .andExpect(jsonPath("$.indicators.maxVolume").value(1820.0))
                .andExpect(jsonPath("$.indicators.alarmEvents").value(1));

        mvc.perform(post("/sensors/{id}/insights", id).header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("period", "ontem"))))
                .andExpect(status().isBadRequest());
    }
}
