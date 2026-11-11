package br.com.saneia.api.sensor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import br.com.saneia.api.support.IntegrationTest;

@DisplayName("Correção 1: endereço estruturado (postalAddress) do equipamento")
class SensorPostalAddressIntegrationTest extends IntegrationTest {

    private static Map<String, Object> postal(String cep, String number, String state, String complement) {
        Map<String, Object> address = new HashMap<>();
        address.put("cep", cep);
        address.put("street", "Praça da Sé");
        address.put("number", number);
        address.put("neighborhood", "Sé");
        address.put("city", "São Paulo");
        address.put("state", state);
        address.put("complement", complement);
        return address;
    }

    private static Map<String, Object> withPostal(String prefix, Map<String, Object> postal) {
        Map<String, Object> input = new HashMap<>(sensorInput(prefix, -23.5503, -46.6340, 150));
        input.put("address", "Praça da Sé, 25 - Sé, São Paulo - SP, 01001-000");
        input.put("neighborhood", "Sé");
        if (postal != null) {
            input.put("postalAddress", postal);
        }
        return input;
    }

    private JsonNode send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            Object payload, int expected) throws Exception {
        return read(mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body(payload)))
                .andExpect(status().is(expected)).andReturn());
    }

    @Test
    @DisplayName("Cria, consulta, edita e consulta de novo com o mesmo endereço (CEP com zero, complemento nulo)")
    void roundTrip() throws Exception {
        JsonNode created = send(post("/admin/sensors"), withPostal("PA-01", postal("01001-000", "25", "sp", null)), 201);
        String id = created.get("id").asText();
        JsonNode address = created.get("postalAddress");
        assertThat(address.get("cep").asText()).isEqualTo("01001000"); // zero inicial preservado, hífen removido
        assertThat(address.get("state").asText()).isEqualTo("SP");
        assertThat(address.get("number").asText()).isEqualTo("25");
        assertThat(address.has("complement")).isTrue();
        assertThat(address.get("complement").isNull()).isTrue();
        assertThat(created.get("address").asText()).startsWith("Praça da Sé, 25");

        String operator = bearer(OPERATOR);
        for (String path : new String[] {"/admin/sensors/{id}", "/sensors/{id}"}) {
            String token = path.startsWith("/admin") ? bearer(ADMIN) : operator;
            mvc.perform(get(path, id).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.postalAddress.cep").value("01001000"))
                    .andExpect(jsonPath("$.postalAddress.street").value("Praça da Sé"))
                    .andExpect(jsonPath("$.postalAddress.city").value("São Paulo"))
                    .andExpect(jsonPath("$.postalAddress.complement").isEmpty())
                    .andExpect(jsonPath("$.neighborhood").value("Sé"))
                    .andExpect(jsonPath("$.coordinate.latitude").value(-23.5503));
        }
        mvc.perform(get("/admin/sensors").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].postalAddress.cep").value(Matchers.hasItem("01001000")));
        mvc.perform(get("/sensors").header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].postalAddress.state").value(Matchers.hasItem("SP")));

        // edição com S/N e complemento: todos os campos do endereço são substituídos juntos
        Map<String, Object> edited = postal("09000000", "s/n", "RJ", "Bloco B");
        edited.put("street", "Rua Nova");
        edited.put("city", "Rio de Janeiro");
        JsonNode updated = send(patch("/admin/sensors/{id}", id), withPostal("PA-01", edited), 200);
        assertThat(updated.get("id").asText()).isEqualTo(id);
        assertThat(updated.at("/postalAddress/number").asText()).isEqualTo("S/N");
        assertThat(updated.at("/postalAddress/complement").asText()).isEqualTo("Bloco B");
        assertThat(updated.at("/postalAddress/street").asText()).isEqualTo("Rua Nova");
        assertThat(updated.at("/postalAddress/state").asText()).isEqualTo("RJ");

        mvc.perform(get("/admin/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(jsonPath("$.postalAddress.cep").value("09000000"))
                .andExpect(jsonPath("$.postalAddress.number").value("S/N"))
                .andExpect(jsonPath("$.postalAddress.city").value("Rio de Janeiro"));
    }

    @Test
    @DisplayName("Valida CEP, UF e campos obrigatórios (400 VALIDATION_ERROR)")
    void validation() throws Exception {
        JsonNode badState = send(post("/admin/sensors"), withPostal("PA-02", postal("01001000", "25", "XX", null)), 400);
        assertThat(badState.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(badState.toString()).contains("UF válida");
        assertThat(badState.at("/errors/0/field").asText()).isEqualTo("postalAddress.state");

        send(post("/admin/sensors"), withPostal("PA-02", postal("1001000", "25", "SP", null)), 400); // 7 dígitos
        send(post("/admin/sensors"), withPostal("PA-02", postal("0100100A", "25", "SP", null)), 400);
        Map<String, Object> noStreet = postal("01001000", "25", "SP", null);
        noStreet.put("street", " ");
        send(post("/admin/sensors"), withPostal("PA-02", noStreet), 400);
        send(post("/admin/sensors"), withPostal("PA-02", postal("01001000", "", "SP", null)), 400);
        // nada foi gravado com os dados inválidos
        send(post("/admin/sensors"), withPostal("PA-02", postal("01001000", "25", "SP", null)), 201);
    }

    @Test
    @DisplayName("Cadastro legado (só endereço livre) continua editável, preservando coordenada, bairro e leituras")
    void legacyEdit() throws Exception {
        JsonNode legacy = send(post("/admin/sensors"), sensorInput("PA-LEG", -23.61, -46.61, 120), 201);
        String id = legacy.get("id").asText();
        assertThat(legacy.has("postalAddress")).isTrue();
        assertThat(legacy.get("postalAddress").isNull()).isTrue();

        mvc.perform(post("/telemetry/readings").header("X-Device-Key", DEVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("sensorId", id, "volume", 500))))
                .andExpect(status().isAccepted());

        Map<String, Object> rename = new HashMap<>(sensorInput("PA-LEG", -23.61, -46.61, 120));
        rename.put("name", "Legado renomeado");
        JsonNode edited = send(patch("/admin/sensors/{id}", id), rename, 200);
        assertThat(edited.get("name").asText()).isEqualTo("Legado renomeado");
        assertThat(edited.get("postalAddress").isNull()).isTrue();
        assertThat(edited.get("neighborhood").asText()).isEqualTo("Setor Teste");
        assertThat(edited.at("/coordinate/latitude").asDouble()).isEqualTo(-23.61);
        assertThat(edited.get("volume").asDouble()).isEqualTo(500.0);
        assertThat(edited.get("readings").size()).isEqualTo(1);

        // o legado ganha o endereço estruturado; uma edição seguinte sem o objeto mantém o que foi salvo
        Map<String, Object> upgrade = new HashMap<>(rename);
        upgrade.put("postalAddress", postal("04567000", "100", "SP", null));
        assertThat(send(patch("/admin/sensors/{id}", id), upgrade, 200).at("/postalAddress/cep").asText())
                .isEqualTo("04567000");
        JsonNode kept = send(patch("/admin/sensors/{id}", id), rename, 200);
        assertThat(kept.at("/postalAddress/cep").asText()).isEqualTo("04567000");
        assertThat(kept.at("/coordinate/longitude").asDouble()).isEqualTo(-46.61);
        assertThat(kept.get("readings").size()).isEqualTo(1);
    }
}
