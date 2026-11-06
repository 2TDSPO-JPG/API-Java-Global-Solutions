package br.com.saneia.api.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import br.com.saneia.api.support.IntegrationTest;

@DisplayName("Relatos: protocolo, recorrência, fotos, complementos e atendimento")
class ReportIntegrationTest extends IntegrationTest {

    private String newResident(String email) throws Exception {
        return "Bearer " + read(mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Morador Teste", "email", email, "password", "senhaForte1"))))
                .andExpect(status().isCreated()).andReturn()).get("token").asText();
    }

    private JsonNode createReport(String token, String category, double lat, double lon) throws Exception {
        return read(mvc.perform(post("/reports").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(reportInput(category, lat, lon))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    @Test
    @DisplayName("Morador abre relato: protocolo e data gerados no servidor, sensor vinculado pela cobertura")
    void createReport() throws Exception {
        String admin = bearer(ADMIN);
        String sensorId = read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("REP-01", -23.50, -46.60, 200))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();

        String resident = newResident("abre@saneia.test");
        JsonNode report = createReport(resident, "Retorno de esgoto", -23.5005, -46.6000);
        assertThat(report.get("protocol").asText()).startsWith("SAN-");
        assertThat(report.get("status").asText()).isEqualTo("received");
        assertThat(report.get("sensorId").asText()).isEqualTo(sensorId);
        assertThat(report.get("photoUrl").isNull()).isTrue();
        assertThat(report.get("resolution").isNull()).isTrue();
        assertThat(report.has("assessment")).isFalse();

        mvc.perform(get("/reports").header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].protocol").value(report.get("protocol").asText()));

        // categoria inválida
        mvc.perform(post("/reports").header(HttpHeaders.AUTHORIZATION, resident).contentType(MediaType.APPLICATION_JSON)
                        .content(body(reportInput("Buraco na rua", -23.40, -46.50))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Regra de 20 dias: 409 REPORT_COOLDOWN com existingReportId; depois do prazo vincula o anterior")
    void cooldownAndRecurrence() throws Exception {
        String resident = newResident("recorrente@saneia.test");
        JsonNode first = createReport(resident, "Vazamento", -23.5100, -46.6100);

        mvc.perform(post("/reports").header(HttpHeaders.AUTHORIZATION, resident).contentType(MediaType.APPLICATION_JSON)
                        .content(body(reportInput("Vazamento", -23.5103, -46.6100)))) // ~33 m
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_COOLDOWN"))
                .andExpect(jsonPath("$.existingReportId").value(first.get("id").asText()))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("poderá ser aberto em")));

        // outra categoria no mesmo lugar não bloqueia
        createReport(resident, "Mau cheiro", -23.5100, -46.6100);
        // mesma categoria a mais de 75 m não bloqueia
        createReport(resident, "Vazamento", -23.5200, -46.6100);

        clock.advance(Duration.ofDays(21));
        JsonNode recurrent = createReport(resident, "Vazamento", -23.5101, -46.6100);
        assertThat(recurrent.get("previousReportId").asText()).isEqualTo(first.get("id").asText());
        assertThat(recurrent.get("previousProtocol").asText()).isEqualTo(first.get("protocol").asText());
    }

    @Test
    @DisplayName("Exclusão só em received, oculta o protocolo e mantém a regra dos 20 dias")
    void deleteKeepsCooldown() throws Exception {
        String resident = newResident("exclui@saneia.test");
        JsonNode report = createReport(resident, "Outro problema", -23.5300, -46.6300);
        String id = report.get("id").asText();

        mvc.perform(delete("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isNoContent());
        mvc.perform(get("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isNotFound());
        mvc.perform(post("/reports").header(HttpHeaders.AUTHORIZATION, resident).contentType(MediaType.APPLICATION_JSON)
                        .content(body(reportInput("Outro problema", -23.5300, -46.6300))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_COOLDOWN"))
                .andExpect(jsonPath("$.existingReportId").doesNotExist());
    }

    @Test
    @DisplayName("Operadora conduz o atendimento; morador não edita após o início")
    void operatorFlow() throws Exception {
        String resident = newResident("fluxo@saneia.test");
        String operator = bearer(OPERATOR);
        String id = createReport(resident, "Retorno de esgoto", -23.5400, -46.6400).get("id").asText();

        // morador não altera status (403) e operadora não fecha sem desfecho (400)
        mvc.perform(patch("/reports/{id}/status", id).header(HttpHeaders.AUTHORIZATION, resident)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "in_progress"))))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/reports/{id}/status", id).header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "closed"))))
                .andExpect(status().isConflict()); // received não pula direto para closed

        mvc.perform(patch("/reports/{id}/status", id).header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "in_progress", "progressNote", "Equipe a caminho."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.progressNote").value("Equipe a caminho."));

        mvc.perform(patch("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, resident)
                        .contentType(MediaType.APPLICATION_JSON).content(body(reportInput("Retorno de esgoto", -23.54, -46.64))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O relato já está em atendimento."));

        mvc.perform(post("/reports/{id}/assessment", id).header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("note", "Verificar a rede da rua."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessment.note").value("Verificar a rede da rua."))
                .andExpect(jsonPath("$.assessment.requestedAt").isNotEmpty());
        mvc.perform(post("/reports/{id}/assessment", id).header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("note", "De novo"))))
                .andExpect(status().isConflict());
        // assessment só aparece para a operadora
        mvc.perform(get("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(jsonPath("$.assessment").doesNotExist());

        mvc.perform(patch("/reports/{id}/status", id).header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "closed"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe o desfecho antes de encerrar o atendimento."));
        mvc.perform(patch("/reports/{id}/status", id).header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "closed", "resolution", "Rede desobstruída."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"))
                .andExpect(jsonPath("$.resolution").value("Rede desobstruída."));

        // notificações geradas pelo consumidor (modo inline nos testes)
        mvc.perform(get("/notifications").header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("report_closed"));
    }

    @Test
    @DisplayName("Morador só vê os próprios relatos")
    void ownership() throws Exception {
        String owner = newResident("dono@saneia.test");
        String other = newResident("outro@saneia.test");
        String id = createReport(owner, "Mau cheiro", -23.5500, -46.6500).get("id").asText();
        mvc.perform(get("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/reports").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Multipart: foto com URL assinada, edição preserva a foto e complementos entram no histórico")
    void photosAndUpdates() throws Exception {
        String resident = newResident("foto@saneia.test");
        byte[] image = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};
        MockMultipartFile reportPart = new MockMultipartFile("report", "", "application/json",
                body(reportInput("Vazamento", -23.5600, -46.6600)).getBytes());
        MockMultipartFile photo = new MockMultipartFile("photo", "relato.jpg", "image/jpeg", image);

        JsonNode created = read(mvc.perform(multipart("/reports").file(reportPart).file(photo)
                        .header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photoUrl").isNotEmpty())
                .andReturn());
        String id = created.get("id").asText();
        URI photoUrl = URI.create(created.get("photoUrl").asText());

        mvc.perform(get(photoUrl.getPath() + "?" + photoUrl.getRawQuery()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(image));
        mvc.perform(get(photoUrl.getPath() + "?expires=1&signature=falsa"))
                .andExpect(status().isForbidden());

        // edição sem nova foto (JSON) mantém a anterior
        mvc.perform(patch("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, resident)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(reportInput("Vazamento", -23.5601, -46.6600))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").isNotEmpty());

        // edição multipart (PATCH) com nova foto
        MockMultipartFile editPart = new MockMultipartFile("report", "", "application/json",
                body(reportInput("Vazamento", -23.5601, -46.6600)).getBytes());
        mvc.perform(multipart(HttpMethod.PATCH, "/reports/{id}", id).file(editPart).file(photo)
                        .header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").isNotEmpty());

        // complemento só texto (JSON) e só foto (multipart)
        mvc.perform(post("/reports/{id}/updates", id).header(HttpHeaders.AUTHORIZATION, resident)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("note", "Piorou hoje cedo."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updates.length()").value(1))
                .andExpect(jsonPath("$.updates[0].note").value("Piorou hoje cedo."))
                .andExpect(jsonPath("$.status").value("received"));
        mvc.perform(multipart("/reports/{id}/updates", id).file(photo).header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updates.length()").value(2))
                .andExpect(jsonPath("$.updates[1].photoUrl").isNotEmpty());
        mvc.perform(post("/reports/{id}/updates", id).header(HttpHeaders.AUTHORIZATION, resident)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("note", "  "))))
                .andExpect(status().isBadRequest());

        // operadora vê o detalhe com complementos
        mvc.perform(get("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updates.length()").value(2))
                .andExpect(jsonPath("$.ownerName").value("Morador Teste"));
    }
}
