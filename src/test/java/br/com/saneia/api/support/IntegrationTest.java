package br.com.saneia.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Base dos testes de integração: contexto completo, H2, MockMvc e relógio controlável. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class IntegrationTest {

    public static final String PASSWORD = "Teste2026!";
    public static final String ADMIN = "admin@saneia.test";
    public static final String OPERATOR = "operadora@saneia.test";
    public static final String RESIDENT = "morador@saneia.test";
    public static final String DEVICE_KEY = "test-device-key";
    /** Começa no instante real: os JWT são validados com o relógio do sistema. */
    public static final Instant START = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(START);
    }

    protected String token(String email) throws Exception {
        return token(email, PASSWORD);
    }

    protected String token(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("token").asText();
    }

    protected String bearer(String email) throws Exception {
        return "Bearer " + token(email);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    protected static Map<String, Object> sensorInput(String prefix, double lat, double lon, double radius) {
        return Map.of(
                "prefix", prefix,
                "name", "Reservatório " + prefix,
                "equipmentType", "Reservatório com sensor de nível",
                "address", "Rua de teste, 100",
                "neighborhood", "Setor Teste",
                "coordinate", Map.of("latitude", lat, "longitude", lon),
                "capacity", 2000,
                "limit", 1800,
                "radiusMeters", radius);
    }

    protected static Map<String, Object> reportInput(String category, double lat, double lon) {
        return Map.of(
                "category", category,
                "description", "Esgoto retornando pelo ralo da cozinha desde ontem.",
                "address", "Rua das Flores, 45",
                "neighborhood", "Setor Teste",
                "coordinate", Map.of("latitude", lat, "longitude", lon));
    }
}
