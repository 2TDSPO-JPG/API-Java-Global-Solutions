package br.com.saneia.api.panel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import br.com.saneia.api.alert.EmailLog;
import br.com.saneia.api.alert.EmailLogRepository;
import br.com.saneia.api.support.IntegrationTest;

@DisplayName("E-mail automático quando uma área entra em risco máximo")
class AlertEmailIntegrationTest extends IntegrationTest {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private EmailLogRepository emailLogs;

    @BeforeEach
    void smtp() {
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    private void reading(String sensorId, double volume) throws Exception {
        mvc.perform(post("/telemetry/readings").header("X-Device-Key", DEVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("sensorId", sensorId, "volume", volume))))
                .andExpect(status().isAccepted());
        clock.advance(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("Alarme envia e-mail aos destinatários; leituras na mesma condição não repetem o envio")
    void alarmSendsEmailOnce() throws Exception {
        mvc.perform(post("/painel/alertas").with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("name", "Plantão").param("email", "plantao@exemplo.com"))
                .andExpect(status().is3xxRedirection());
        String sensorId = read(mvc.perform(post("/admin/sensors").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(sensorInput("MAIL-01", -23.80, -46.80, 200))))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();

        reading(sensorId, 800);   // normal
        reading(sensorId, 1600);  // alerta (85% do limite): não é risco máximo
        verify(mailSender, never()).send(any(MimeMessage.class));

        reading(sensorId, 1850);  // limite atingido → risco máximo
        reading(sensorId, 1900);  // mesma condição: não repete
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(sent.capture());
        assertThat(sent.getValue().getSubject()).contains("Risco máximo").contains("MAIL-01").contains("Setor Teste");
        assertThat(sent.getValue().getAllRecipients()[0].toString()).isEqualTo("plantao@exemplo.com");
        assertThat(emailLogs.findAll()).anyMatch(log -> log.getStatus() == EmailLog.Status.SENT
                && "plantao@exemplo.com".equals(log.getRecipient()));
    }

    @Test
    @DisplayName("E-mail de teste pelo painel")
    void testEmail() throws Exception {
        mvc.perform(post("/painel/alertas/teste").with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("email", "teste@exemplo.com"))
                .andExpect(status().is3xxRedirection());
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
}
