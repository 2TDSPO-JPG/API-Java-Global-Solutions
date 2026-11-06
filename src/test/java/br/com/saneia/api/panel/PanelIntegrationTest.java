package br.com.saneia.api.panel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.saneia.api.alert.EmailLog;
import br.com.saneia.api.alert.EmailLogRepository;
import br.com.saneia.api.support.IntegrationTest;
import br.com.saneia.api.user.AppUserRepository;

@DisplayName("Painel administrativo (Thymeleaf) e risco por área")
class PanelIntegrationTest extends IntegrationTest {

    private static final RequestPostProcessor ADMIN_SESSION = user(ADMIN).roles("ADMIN");

    @Autowired
    private AppUserRepository users;

    @Autowired
    private EmailLogRepository emailLogs;

    private static String sensorId;
    private static String reportId;

    @BeforeEach
    void scenario() throws Exception {
        if (sensorId != null) {
            return;
        }
        String admin = bearer(ADMIN);
        sensorId = read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(sensorInput("PNL-01", -23.70, -46.70, 300))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        reportId = read(mvc.perform(post("/reports").header(HttpHeaders.AUTHORIZATION, bearer(RESIDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(reportInput("Retorno de esgoto", -23.7001, -46.70))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        for (double volume : new double[] {900, 1850}) {
            mvc.perform(post("/telemetry/readings").header("X-Device-Key", DEVICE_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("sensorId", sensorId, "volume", volume))))
                    .andExpect(status().isAccepted());
            clock.advance(Duration.ofMinutes(5));
        }
    }

    @Test
    @DisplayName("Sem login redireciona para a tela de login; morador não entra no painel")
    void requiresAdminLogin() throws Exception {
        mvc.perform(get("/painel")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/painel/login"));
        mvc.perform(get("/painel/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Painel admin")));
        mvc.perform(get("/painel").with(user(RESIDENT).roles("RESIDENT"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Login por formulário com as contas da API")
    void formLoginWorks() throws Exception {
        mvc.perform(formLogin("/painel/login").userParameter("email").passwordParam("senha")
                        .user(ADMIN).password(PASSWORD))
                .andExpect(authenticated().withRoles("ADMIN"))
                .andExpect(redirectedUrl("/painel"));
        mvc.perform(formLogin("/painel/login").userParameter("email").passwordParam("senha")
                        .user(ADMIN).password("errada"))
                .andExpect(redirectedUrl("/painel/login?erro"));
    }

    @Test
    @DisplayName("Visão geral mostra chamados, equipamentos e área em risco máximo")
    void dashboard() throws Exception {
        mvc.perform(get("/painel").with(ADMIN_SESSION))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Visão geral")))
                .andExpect(content().string(containsString("Setor Teste")))
                .andExpect(content().string(containsString("Risco máximo")))
                .andExpect(content().string(containsString("PNL-01")));
        mvc.perform(get("/painel/areas").with(ADMIN_SESSION))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Em alarme: PNL-01")));
    }

    @Test
    @DisplayName("Todas as telas de dados abrem para o admin")
    void dataPages() throws Exception {
        mvc.perform(get("/painel/chamados").with(ADMIN_SESSION)).andExpect(status().isOk())
                .andExpect(content().string(containsString("SAN-")));
        mvc.perform(get("/painel/chamados").param("status", "received").param("q", "teste").with(ADMIN_SESSION))
                .andExpect(status().isOk());
        mvc.perform(get("/painel/chamados/{id}", reportId).with(ADMIN_SESSION)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Esgoto retornando")));
        mvc.perform(get("/painel/equipamentos").with(ADMIN_SESSION)).andExpect(status().isOk())
                .andExpect(content().string(containsString("PNL-01")));
        mvc.perform(get("/painel/equipamentos/{id}", sensorId).with(ADMIN_SESSION)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Últimas leituras")));
        mvc.perform(get("/painel/usuarios").with(ADMIN_SESSION)).andExpect(status().isOk())
                .andExpect(content().string(containsString(OPERATOR)));
        mvc.perform(get("/painel/alertas").with(ADMIN_SESSION)).andExpect(status().isOk())
                .andExpect(content().string(containsString("SMTP não configurado")));
        mvc.perform(get("/painel/chamados/{id}", 999999).with(ADMIN_SESSION))
                .andExpect(content().string(containsString("Chamado não encontrado")));
    }

    @Test
    @DisplayName("Admin atualiza o atendimento pelo painel (com CSRF)")
    void updateStatusFromPanel() throws Exception {
        mvc.perform(post("/painel/chamados/{id}/status", reportId).with(ADMIN_SESSION)
                        .param("status", "in_progress").param("progressNote", "Equipe acionada"))
                .andExpect(status().isForbidden()); // sem token CSRF
        mvc.perform(post("/painel/chamados/{id}/status", reportId).with(ADMIN_SESSION).with(csrf())
                        .param("status", "in_progress").param("progressNote", "Equipe acionada"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/reports/{id}", reportId).header(HttpHeaders.AUTHORIZATION, bearer(OPERATOR)))
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.progressNote").value("Equipe acionada"));
    }

    @Test
    @DisplayName("Admin cria conta de operadora e cadastra destinatário de alerta")
    void adminActions() throws Exception {
        mvc.perform(post("/painel/usuarios").with(ADMIN_SESSION).with(csrf())
                        .param("name", "Plantão Norte").param("email", "plantao@saneia.test")
                        .param("password", "SenhaForte9").param("role", "operator"))
                .andExpect(status().is3xxRedirection());
        assertThat(users.findByEmailIgnoreCase("plantao@saneia.test")).isPresent();

        mvc.perform(post("/painel/usuarios").with(ADMIN_SESSION).with(csrf())
                        .param("name", "X").param("email", "invalido").param("password", "123").param("role", "operator"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe um e-mail válido")));

        mvc.perform(post("/painel/alertas").with(ADMIN_SESSION).with(csrf())
                        .param("name", "Plantão").param("email", "plantao@saneia.test"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/painel/alertas").with(ADMIN_SESSION))
                .andExpect(content().string(containsString("plantao@saneia.test")));
    }

    @Test
    @DisplayName("Sem SMTP o alerta de risco máximo fica registrado como não enviado")
    void alarmIsLoggedWithoutSmtp() {
        assertThat(emailLogs.findAll()).anyMatch(log -> log.getStatus() == EmailLog.Status.SKIPPED
                && log.getSubject().contains("Risco máximo") && log.getSubject().contains("PNL-01"));
    }
}
