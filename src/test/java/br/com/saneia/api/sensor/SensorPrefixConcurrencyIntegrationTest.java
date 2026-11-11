package br.com.saneia.api.sensor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.com.saneia.api.support.IntegrationTest;

@DisplayName("Correção 2: prefixo único entre equipamentos ativos, garantido no banco")
class SensorPrefixConcurrencyIntegrationTest extends IntegrationTest {

    @Autowired
    private SensorRepository sensors;

    /** Dispara as requisições ao mesmo tempo (portão de largada) e devolve as respostas. */
    private List<MockHttpServletResponse> concurrently(List<MockHttpServletRequestBuilder> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (MockHttpServletRequestBuilder request : requests) {
                Callable<MockHttpServletResponse> call = () -> {
                    ready.countDown();
                    go.await();
                    return mvc.perform(request).andReturn().getResponse();
                };
                futures.add(pool.submit(call));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long count(List<MockHttpServletResponse> responses, int status) {
        return responses.stream().filter(response -> response.getStatus() == status).count();
    }

    private long activeWithPrefix(String prefix) {
        return sensors.findByActiveTrue().stream().filter(s -> s.getPrefix().equalsIgnoreCase(prefix)).count();
    }

    @Test
    @DisplayName("8 cadastros simultâneos com o mesmo prefixo: exatamente um 201, sete 409 e um único ativo")
    void eightConcurrentCreates() throws Exception {
        String admin = bearer(ADMIN);
        String payload = body(sensorInput("CONC-01", -23.70, -46.70, 100));
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            requests.add(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                    .contentType(MediaType.APPLICATION_JSON).content(payload));
        }
        List<MockHttpServletResponse> responses = concurrently(requests);

        assertThat(count(responses, 201)).as("respostas 201").isEqualTo(1);
        assertThat(count(responses, 409)).as("respostas 409").isEqualTo(7);
        for (MockHttpServletResponse response : responses) {
            if (response.getStatus() == 409) {
                assertThat(response.getContentAsString()).contains("Já existe um equipamento com esse prefixo.");
            }
        }
        assertThat(activeWithPrefix("CONC-01")).isEqualTo(1);
    }

    @Test
    @DisplayName("Edição concorrente de dois equipamentos para o mesmo prefixo: um 200 e um 409, IDs estáveis")
    void concurrentEdits() throws Exception {
        String admin = bearer(ADMIN);
        String first = read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("EDIT-A", -23.71, -46.71, 100))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        String second = read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("EDIT-B", -23.72, -46.72, 100))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();

        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        for (String id : List.of(first, second)) {
            requests.add(patch("/admin/sensors/{id}", id).header(HttpHeaders.AUTHORIZATION, admin)
                    .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("edit-alvo", -23.73, -46.73, 100))));
        }
        List<MockHttpServletResponse> responses = concurrently(requests);
        assertThat(count(responses, 200)).isEqualTo(1);
        assertThat(count(responses, 409)).isEqualTo(1);
        assertThat(activeWithPrefix("EDIT-ALVO")).isEqualTo(1);
        // nenhum equipamento mudou de ID nem foi duplicado
        assertThat(sensors.findById(Long.valueOf(first))).isPresent();
        assertThat(sensors.findById(Long.valueOf(second))).isPresent();
        assertThat(activeWithPrefix("EDIT-A") + activeWithPrefix("EDIT-B")).isEqualTo(1);
    }

    @Test
    @DisplayName("Maiúsculas e minúsculas conflitam; o prefixo de um equipamento retirado pode ser reaproveitado")
    void caseInsensitiveAndReuseAfterRetire() throws Exception {
        String admin = bearer(ADMIN);
        String original = read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("Reuso-1", -23.74, -46.74, 100))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();

        mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("REUSO-1", -23.75, -46.75, 100))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Já existe um equipamento com esse prefixo."));

        // editar o próprio equipamento mantendo o prefixo (com outra caixa) não conflita consigo mesmo
        Map<String, Object> same = new HashMap<>(sensorInput("reuso-1", -23.74, -46.74, 100));
        mvc.perform(patch("/admin/sensors/{id}", original).header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(same)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original))
                .andExpect(jsonPath("$.prefix").value("REUSO-1"));

        // duas retiradas seguidas do mesmo prefixo: os históricos inativos não conflitam entre si
        for (int round = 0; round < 2; round++) {
            String current = round == 0 ? original : read(mvc.perform(post("/admin/sensors")
                            .header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                            .content(body(sensorInput("reuso-1", -23.76, -46.76, 100))))
                    .andExpect(status().isCreated()).andReturn()).get("id").asText();
            mvc.perform(delete("/admin/sensors/{id}", current).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isNoContent());
        }
        String reused = read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(sensorInput("REUSO-1", -23.77, -46.77, 100))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        assertThat(reused).isNotEqualTo(original);
        assertThat(activeWithPrefix("REUSO-1")).isEqualTo(1);
        Sensor retired = sensors.findById(Long.valueOf(original)).orElseThrow();
        assertThat(retired.isActive()).isFalse();
        assertThat(retired.getActivePrefixKey()).isNull();
        assertThat(sensors.findById(Long.valueOf(reused)).orElseThrow().getActivePrefixKey()).isEqualTo("REUSO-1");
    }
}
