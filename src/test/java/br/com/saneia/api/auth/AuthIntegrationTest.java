package br.com.saneia.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import br.com.saneia.api.support.IntegrationTest;

@DisplayName("Autenticação, JWT e papéis")
class AuthIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("Cadastro público cria morador e devolve sessão com token")
    void signupCreatesResident() throws Exception {
        JsonNode session = read(mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Maria Silva", "email", "Maria@Exemplo.com", "password", "senhaForte1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("resident"))
                .andExpect(jsonPath("$.user.email").value("maria@exemplo.com"))
                .andReturn());
        assertThat(session.get("user").get("id").isTextual()).isTrue();

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Outra", "email", "maria@exemplo.com", "password", "senhaForte1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe uma conta com este e-mail."));
    }

    @Test
    @DisplayName("Bean Validation devolve 400 com mensagem padronizada")
    void signupValidation() throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "A", "email", "invalido", "password", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("Login com senha errada responde 401")
    void wrongPassword() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", OPERATOR, "password", "errada123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha incorretos."));
    }

    @Test
    @DisplayName("Sem token: 401. Token inválido: 401")
    void unauthenticated() throws Exception {
        mvc.perform(get("/reports")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/reports").header(HttpHeaders.AUTHORIZATION, "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Morador e operadora não acessam /admin (403); admin lista usuários paginados")
    void adminRoutesRequireAdmin() throws Exception {
        mvc.perform(get("/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(RESIDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").param("page", "0").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].role").exists());
    }

    @Test
    @DisplayName("Morador não acessa o monitoramento da operadora (403)")
    void residentCannotSeeSensors() throws Exception {
        mvc.perform(get("/sensors").header(HttpHeaders.AUTHORIZATION, bearer(RESIDENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/sensors").header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Swagger/OpenAPI é público")
    void openApiIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Saneia API"));
    }

    @Test
    @DisplayName("Health e /about são públicos para o smoke test da Release")
    void publicEndpoints() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/about")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Saneia API"))
                .andExpect(jsonPath("$.queues.length()").value(3));
    }
}
