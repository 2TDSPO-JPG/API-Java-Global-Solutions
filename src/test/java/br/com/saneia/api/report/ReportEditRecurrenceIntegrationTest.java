package br.com.saneia.api.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.com.saneia.api.support.IntegrationTest;

@DisplayName("Correção 3: regra de recorrência (20 dias / 75 m) também na edição do relato")
class ReportEditRecurrenceIntegrationTest extends IntegrationTest {

    private String newResident(String email) throws Exception {
        return "Bearer " + read(mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Morador Recorrência", "email", email, "password", "senhaForte1"))))
                .andExpect(status().isCreated()).andReturn()).get("token").asText();
    }

    private JsonNode create(String token, String category, double lat, double lon) throws Exception {
        return read(mvc.perform(post("/reports").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(reportInput(category, lat, lon))))
                .andExpect(status().isCreated()).andReturn());
    }

    private MockHttpServletRequestBuilder edit(String token, String id, Map<String, Object> input) throws Exception {
        return patch("/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body(input));
    }

    @Test
    @DisplayName("Sequência do documento: editar Mau cheiro para Vazamento no mesmo ponto devolve 409 e nada muda")
    void documentedSequence() throws Exception {
        String resident = newResident("sequencia@saneia.test");
        JsonNode leak = create(resident, "Vazamento", -23.6000, -46.6000);
        JsonNode smell = create(resident, "Mau cheiro", -23.6000, -46.6000);
        String smellId = smell.get("id").asText();

        mvc.perform(edit(resident, smellId, reportInput("Vazamento", -23.6000, -46.6000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_COOLDOWN"))
                .andExpect(jsonPath("$.existingReportId").value(leak.get("id").asText()))
                .andExpect(jsonPath("$.nextEligibleAt").isNotEmpty())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("poderá ser aberto em")));

        // o segundo relato continua com a categoria, o local, o protocolo e a data de antes
        mvc.perform(get("/reports/{id}", smellId).header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Mau cheiro"))
                .andExpect(jsonPath("$.coordinate.latitude").value(-23.6000))
                .andExpect(jsonPath("$.protocol").value(smell.get("protocol").asText()))
                .andExpect(jsonPath("$.createdAt").value(smell.get("createdAt").asText()));

        // e a terceira abertura continua bloqueada, como antes
        mvc.perform(post("/reports").header(HttpHeaders.AUTHORIZATION, resident)
                        .contentType(MediaType.APPLICATION_JSON).content(body(reportInput("Vazamento", -23.6000, -46.6000))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Mover o relato para perto de outro da mesma categoria é bloqueado; longe dele é permitido")
    void locationChange() throws Exception {
        String resident = newResident("local@saneia.test");
        JsonNode anchor = create(resident, "Retorno de esgoto", -23.6100, -46.6100);
        JsonNode far = create(resident, "Retorno de esgoto", -23.6120, -46.6100); // ~222 m
        String farId = far.get("id").asText();

        mvc.perform(edit(resident, farId, reportInput("Retorno de esgoto", -23.6104, -46.6100))) // ~44 m do outro
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_COOLDOWN"))
                .andExpect(jsonPath("$.existingReportId").value(anchor.get("id").asText()));
        mvc.perform(get("/reports/{id}", farId).header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(jsonPath("$.coordinate.latitude").value(-23.6120));

        // pequena correção de posição que continua a mais de 75 m do outro protocolo
        mvc.perform(edit(resident, farId, reportInput("Retorno de esgoto", -23.6115, -46.6100))) // ~167 m
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coordinate.latitude").value(-23.6115))
                .andExpect(jsonPath("$.protocol").value(far.get("protocol").asText()));
    }

    @Test
    @DisplayName("Editar texto, referência ou foto do mesmo protocolo continua liberado: o relato não bloqueia a si mesmo")
    void contentEditsAndSelf() throws Exception {
        String resident = newResident("texto@saneia.test");
        JsonNode report = create(resident, "Vazamento", -23.6200, -46.6200);
        String id = report.get("id").asText();

        Map<String, Object> text = new HashMap<>(reportInput("Vazamento", -23.6200, -46.6200));
        text.put("description", "Vazamento aumentou e agora escorre pela calçada inteira.");
        text.put("address", "Rua das Flores, 45 · em frente ao portão azul");
        mvc.perform(edit(resident, id, text))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Vazamento aumentou e agora escorre pela calçada inteira."))
                .andExpect(jsonPath("$.protocol").value(report.get("protocol").asText()))
                .andExpect(jsonPath("$.createdAt").value(report.get("createdAt").asText()));

        // mover 30 m (mesma ocorrência): só existe o próprio relato por perto
        mvc.perform(edit(resident, id, reportInput("Vazamento", -23.6203, -46.6200)))
                .andExpect(status().isOk());

        MockMultipartFile part = new MockMultipartFile("report", "", "application/json",
                body(reportInput("Vazamento", -23.6203, -46.6200)).getBytes());
        MockMultipartFile photo = new MockMultipartFile("photo", "nova.jpg", "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x05});
        mvc.perform(multipart(HttpMethod.PATCH, "/reports/{id}", id).file(part).file(photo)
                        .header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").isNotEmpty());
    }

    @Test
    @DisplayName("Protocolo anterior encerrado não bloqueia; excluído continua bloqueando (sem existingReportId)")
    void closedAndDeleted() throws Exception {
        String resident = newResident("encerrado@saneia.test");
        String operator = bearer(OPERATOR);
        String closed = create(resident, "Vazamento", -23.6300, -46.6300).get("id").asText();
        mvc.perform(patch("/reports/{id}/status", closed).header(HttpHeaders.AUTHORIZATION, operator)
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "in_progress"))))
                .andExpect(status().isOk());
        mvc.perform(patch("/reports/{id}/status", closed).header(HttpHeaders.AUTHORIZATION, operator)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("status", "closed", "resolution", "Reparo concluído."))))
                .andExpect(status().isOk());
        String smell = create(resident, "Mau cheiro", -23.6300, -46.6300).get("id").asText();
        mvc.perform(edit(resident, smell, reportInput("Vazamento", -23.6300, -46.6300)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Vazamento"))
                .andExpect(jsonPath("$.previousReportId").isEmpty());

        String deleted = create(resident, "Outro problema", -23.6400, -46.6400).get("id").asText();
        mvc.perform(delete("/reports/{id}", deleted).header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isNoContent());
        String other = create(resident, "Mau cheiro", -23.6400, -46.6400).get("id").asText();
        mvc.perform(edit(resident, other, reportInput("Outro problema", -23.6400, -46.6400)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_COOLDOWN"))
                .andExpect(jsonPath("$.existingReportId").doesNotExist());
    }

    @Test
    @DisplayName("Passados 20 dias a edição é liberada e vincula o protocolo anterior aberto (recorrência)")
    void afterInterval() throws Exception {
        String resident = newResident("prazo@saneia.test");
        JsonNode first = create(resident, "Vazamento", -23.6500, -46.6500);
        clock.advance(Duration.ofDays(21));
        String smell = create(resident, "Mau cheiro", -23.6500, -46.6500).get("id").asText();
        mvc.perform(edit(resident, smell, reportInput("Vazamento", -23.6501, -46.6500)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Vazamento"))
                .andExpect(jsonPath("$.previousReportId").value(first.get("id").asText()));
    }

    @Test
    @DisplayName("Criação e edição simultâneas para o mesmo destino: uma passa e a outra recebe 409")
    void concurrentCreateAndEdit() throws Exception {
        String resident = newResident("corrida@saneia.test");
        String smell = create(resident, "Mau cheiro", -23.6600, -46.6600).get("id").asText();

        List<MockHttpServletRequestBuilder> requests = List.of(
                post("/reports").header(HttpHeaders.AUTHORIZATION, resident).contentType(MediaType.APPLICATION_JSON)
                        .content(body(reportInput("Vazamento", -23.6600, -46.6600))),
                edit(resident, smell, reportInput("Vazamento", -23.6600, -46.6600)));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<MockHttpServletResponse> responses = new ArrayList<>();
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (MockHttpServletRequestBuilder request : requests) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return mvc.perform(request).andReturn().getResponse();
                }));
            }
            go.countDown();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        long ok = responses.stream().filter(r -> r.getStatus() == 201 || r.getStatus() == 200).count();
        long conflict = responses.stream().filter(r -> r.getStatus() == 409).count();
        assertThat(ok).as("uma operação passa").isEqualTo(1);
        assertThat(conflict).as("a outra recebe 409").isEqualTo(1);

        JsonNode list = read(mvc.perform(get("/reports").header(HttpHeaders.AUTHORIZATION, resident))
                .andExpect(status().isOk()).andReturn());
        long leaks = 0;
        for (JsonNode node : list) {
            if ("Vazamento".equals(node.get("category").asText())) {
                leaks++;
            }
        }
        assertThat(leaks).as("um único Vazamento no ponto").isEqualTo(1);
    }
}
