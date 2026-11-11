package br.com.saneia.api.sensor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import br.com.saneia.api.support.IntegrationTest;

/** Depois de coordenar com o mobile: SANEIA_REQUIRE_POSTAL_ADDRESS=true exige o objeto só nos novos cadastros. */
@TestPropertySource(properties = "saneia.sensors.require-postal-address=true")
@DisplayName("Correção 1: postalAddress obrigatório nos novos cadastros (legados continuam editáveis)")
class SensorPostalAddressRequiredIntegrationTest extends IntegrationTest {

    @Autowired
    private SensorRepository sensors;

    @Test
    @DisplayName("POST sem postalAddress → 400; edição de equipamento legado sem o objeto → 200")
    void requiredOnCreateOnly() throws Exception {
        String admin = bearer(ADMIN);
        mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("REQ-01", -23.85, -46.85, 100))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe o endereço estruturado (postalAddress) do equipamento."));

        Map<String, Object> complete = new HashMap<>(sensorInput("REQ-01", -23.85, -46.85, 100));
        complete.put("postalAddress", Map.of("cep", "01310100", "street", "Avenida Paulista", "number", "1000",
                "neighborhood", "Bela Vista", "city", "São Paulo", "state", "SP"));
        mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(complete)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postalAddress.cep").value("01310100"));

        // equipamento legado (criado antes da regra, só com endereço livre)
        Sensor legacy = new Sensor(clock.instant());
        legacy.updateCatalog("LEG-01", "Legado", "Reservatório", "Rua Antiga, 1", "Centro", -23.86, -46.86,
                2000, 1800, 100, 30, clock.instant());
        String id = String.valueOf(sensors.save(legacy).getId());
        mvc.perform(patch("/admin/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("LEG-01", -23.86, -46.86, 150))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radiusMeters").value(150.0))
                .andExpect(jsonPath("$.postalAddress").isEmpty());
    }
}
