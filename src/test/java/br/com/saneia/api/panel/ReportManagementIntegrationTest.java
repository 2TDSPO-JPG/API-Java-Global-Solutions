package br.com.saneia.api.panel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.saneia.api.support.IntegrationTest;
import br.com.saneia.api.user.AppUserRepository;

@DisplayName("Gestão de chamados: edição, prioridade/SLA, desfecho, reabertura, lote, CSV, mapa e IA")
class ReportManagementIntegrationTest extends IntegrationTest {

    private static final RequestPostProcessor ADMIN_SESSION = user(ADMIN).roles("ADMIN");

    @Autowired
    private AppUserRepository users;

    private String resident(String email) throws Exception {
        return "Bearer " + read(mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Morador " + email, "email", email, "password", "senhaForte1"))))
                .andExpect(status().isCreated()).andReturn()).get("token").asText();
    }

    private String report(String token, String category, double lat, double lon) throws Exception {
        return read(mvc.perform(post("/reports").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(reportInput(category, lat, lon))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private String sensor(String prefix, double lat, double lon) throws Exception {
        return read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput(prefix, lat, lon, 300))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private void reading(String sensorId, double volume) throws Exception {
        mvc.perform(post("/telemetry/readings").header("X-Device-Key", DEVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("sensorId", sensorId, "volume", volume))))
                .andExpect(status().isAccepted());
        clock.advance(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("Prioridade sugerida e SLA na abertura, visíveis só para a operadora")
    void priorityAndSla() throws Exception {
        String id = report(resident("sla@saneia.test"), "Retorno de esgoto", -23.9000, -46.9000);
        mvc.perform(get("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(jsonPath("$.priority").value("high"))
                .andExpect(jsonPath("$.dueAt").isNotEmpty())
                .andExpect(jsonPath("$.overdue").value(false));
        clock.advance(Duration.ofHours(25));
        mvc.perform(get("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(jsonPath("$.overdue").value(true));
        mvc.perform(get("/painel/chamados").param("atrasados", "true").with(ADMIN_SESSION))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("atrasado")));
    }

    @Test
    @DisplayName("Admin edita categoria, prioridade, responsável e vínculo de sensor; tudo vai para o histórico")
    void editFromPanel() throws Exception {
        String sensorId = sensor("EDT-01", -23.9100, -46.9100);
        String id = report(resident("edita@saneia.test"), "Mau cheiro", -23.9100, -46.9100);
        Long operatorId = users.findByEmailIgnoreCase(OPERATOR).orElseThrow().getId();

        mvc.perform(post("/painel/chamados/{id}/editar", id).with(ADMIN_SESSION).with(csrf())
                        .param("category", "Vazamento").param("description", "Vazamento forte na calçada.")
                        .param("address", "Rua A, 10").param("neighborhood", "Setor Teste")
                        .param("priority", "critical").param("assigneeId", String.valueOf(operatorId))
                        .param("sensorMode", "none"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("sucesso"));

        mvc.perform(get("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(jsonPath("$.category").value("Vazamento"))
                .andExpect(jsonPath("$.priority").value("critical"))
                .andExpect(jsonPath("$.assigneeName").value("Equipe operadora"))
                .andExpect(jsonPath("$.sensorId").isEmpty());
        mvc.perform(get("/painel/chamados/{id}", id).with(ADMIN_SESSION))
                .andExpect(content().string(containsString("Prioridade alterada")))
                .andExpect(content().string(containsString("Responsável definido")))
                .andExpect(content().string(containsString("Vínculo com sensor")))
                .andExpect(content().string(containsString("categoria (Mau cheiro → Vazamento)")));

        // vínculo manual a um sensor específico
        mvc.perform(post("/painel/chamados/{id}/editar", id).with(ADMIN_SESSION).with(csrf())
                        .param("category", "Vazamento").param("description", "Vazamento forte na calçada.")
                        .param("address", "Rua A, 10").param("neighborhood", "Setor Teste")
                        .param("priority", "critical").param("assigneeId", "").param("sensorMode", sensorId))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(jsonPath("$.sensorId").value(sensorId))
                .andExpect(jsonPath("$.assigneeName").doesNotExist());
    }

    @Test
    @DisplayName("Encerrado como 'Estabilizado' reabre sozinho quando o sensor volta ao alarme")
    void stabilizedReopensOnAlarm() throws Exception {
        String sensorId = sensor("OBS-01", -23.9200, -46.9200);
        String token = resident("observa@saneia.test");
        String id = report(token, "Retorno de esgoto", -23.9201, -46.9200);
        String operator = bearer(OPERATOR);
        mvc.perform(patch("/reports/{id}/status", id)
                        .header(HttpHeaders.AUTHORIZATION, operator).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "in_progress"))))
                .andExpect(status().isOk());
        mvc.perform(patch("/reports/{id}/status", id)
                        .header(HttpHeaders.AUTHORIZATION, operator).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "closed", "resolution", "Nível estabilizado após esgotamento.",
                                "outcome", "stabilized"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("stabilized"));

        reading(sensorId, 900);
        reading(sensorId, 1850); // alarme durante a observação

        mvc.perform(get("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.reopenCount").value(1))
                .andExpect(jsonPath("$.closedAt").doesNotExist());
        mvc.perform(get("/painel/chamados/{id}", id).with(ADMIN_SESSION))
                .andExpect(content().string(containsString("Reaberto automaticamente")));
        mvc.perform(get("/notifications").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$[0].message").value(containsString("reaberto")));
    }

    @Test
    @DisplayName("Reabertura manual (API e painel) e nota interna")
    void manualReopenAndNotes() throws Exception {
        String id = report(resident("reabre@saneia.test"), "Vazamento", -23.9300, -46.9300);
        String operator = bearer(OPERATOR);
        mvc.perform(post("/reports/{id}/reopen", id).header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("note", "Voltou"))))
                .andExpect(status().isConflict()); // ainda está aberto

        mvc.perform(post("/painel/chamados/lote").with(ADMIN_SESSION).with(csrf())
                        .param("ids", id).param("acao", "close").param("resolution", "Resolvido em vistoria")
                        .param("outcome", "resolved"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/reports/{id}/reopen", id).header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("note", "Morador relatou retorno"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.reopenCount").value(1));

        mvc.perform(post("/painel/chamados/{id}/nota", id).with(ADMIN_SESSION).with(csrf())
                        .param("nota", "Caminhão limpa-fossa agendado"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/painel/chamados/{id}", id).with(ADMIN_SESSION))
                .andExpect(content().string(containsString("Caminhão limpa-fossa agendado")))
                .andExpect(content().string(containsString("Reaberto pela equipe")));
    }

    @Test
    @DisplayName("Ação em lote encerra vários chamados e informa os que não puderam ser alterados")
    void batchClose() throws Exception {
        String a = report(resident("lote-a@saneia.test"), "Vazamento", -23.9400, -46.9400);
        String b = report(resident("lote-b@saneia.test"), "Vazamento", -23.9401, -46.9400);
        mvc.perform(post("/painel/chamados/lote").with(ADMIN_SESSION).with(csrf())
                        .param("ids", a, b).param("acao", "close").param("resolution", "Atendimento conjunto da rua")
                        .param("outcome", "resolved"))
                .andExpect(flash().attribute("sucesso", "2 chamado(s) atualizado(s) em lote."));
        mvc.perform(get("/reports/{id}", b).header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(jsonPath("$.status").value("closed"))
                .andExpect(jsonPath("$.resolution").value("Atendimento conjunto da rua"));

        mvc.perform(post("/painel/chamados/lote").with(ADMIN_SESSION).with(csrf())
                        .param("ids", a).param("acao", "close").param("resolution", "De novo"))
                .andExpect(flash().attribute("erro", containsString("já está encerrado")));
    }

    @Test
    @DisplayName("Exporta CSV, abre o mapa ao vivo, gera boletim e sugere resposta (fallback sem chave de IA)")
    void exportMapAndAi() throws Exception {
        String sensorId = sensor("MAP-01", -23.9500, -46.9500);
        String id = report(resident("mapa@saneia.test"), "Retorno de esgoto", -23.9501, -46.9500);

        byte[] csv = mvc.perform(get("/painel/chamados.csv").with(ADMIN_SESSION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("chamados-saneia.csv")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).contains("protocolo;categoria;bairro").contains("Retorno de esgoto");

        mvc.perform(get("/painel/mapa").with(ADMIN_SESSION))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MAP-01")))
                .andExpect(content().string(containsString("Mapa ao vivo")));
        assertThat(sensorId).isNotBlank();

        mvc.perform(post("/painel/boletim").with(ADMIN_SESSION).with(csrf()))
                .andExpect(flash().attribute("boletim", containsString("chamado(s) aberto(s)")))
                .andExpect(flash().attribute("boletimIa", false));

        mvc.perform(post("/painel/chamados/{id}/sugestao", id).with(ADMIN_SESSION).with(csrf()).param("tipo", "andamento"))
                .andExpect(flash().attribute("sugestao", containsString("SAN-")));
    }
}
