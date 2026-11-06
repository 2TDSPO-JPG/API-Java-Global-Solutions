package br.com.saneia.api.alert;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import br.com.saneia.api.config.SaneiaProperties;
import br.com.saneia.api.messaging.Events;
import br.com.saneia.api.report.ReportRepository;
import br.com.saneia.api.report.ReportStatus;
import br.com.saneia.api.sensor.Sensor;
import br.com.saneia.api.sensor.SensorRepository;

/**
 * Envia o e-mail de "risco máximo" quando um ponto entra em alarme. Roda no consumidor assíncrono da fila
 * saneia.notifications (evento sensor.alert), então o SMTP lento ou fora do ar não atrasa a ingestão das leituras.
 * Cada tentativa fica registrada em sn_email_logs (SENT, FAILED ou SKIPPED) e aparece no painel.
 */
@Service
public class AlertEmailService {

    private static final Logger log = LoggerFactory.getLogger(AlertEmailService.class);
    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    static final String EVENT_MAX_RISK = "risco_maximo";
    static final String EVENT_TEST = "teste";

    private final SensorRepository sensors;
    private final ReportRepository reports;
    private final AlertRecipientRepository recipients;
    private final EmailLogRepository logs;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final ITemplateEngine templates;
    private final SaneiaProperties properties;
    private final Clock clock;

    public AlertEmailService(SensorRepository sensors, ReportRepository reports, AlertRecipientRepository recipients,
            EmailLogRepository logs, ObjectProvider<JavaMailSender> mailSender, ITemplateEngine templates,
            SaneiaProperties properties, Clock clock) {
        this.sensors = sensors;
        this.reports = reports;
        this.recipients = recipients;
        this.logs = logs;
        this.mailSender = mailSender;
        this.templates = templates;
        this.properties = properties;
        this.clock = clock;
    }

    /** Só o alarme (limite operacional atingido) é risco máximo. Alerta de previsão e offline não disparam e-mail. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSensorAlert(Events.SensorAlert event) {
        if (!"alarm".equals(event.status())) {
            return;
        }
        Sensor sensor = sensors.findById(event.sensorId()).orElse(null);
        if (sensor == null) {
            return;
        }
        long openReports = reports.countByNeighborhoodIgnoreCaseAndStatusNotAndDeletedAtIsNull(
                sensor.getNeighborhood(), ReportStatus.CLOSED);
        String subject = "[Saneia] Risco máximo em " + sensor.getNeighborhood() + " · " + sensor.getPrefix();

        Context context = new Context(Locale.of("pt", "BR"));
        context.setVariable("area", sensor.getNeighborhood());
        context.setVariable("prefixo", sensor.getPrefix());
        context.setVariable("equipamento", sensor.getName());
        context.setVariable("endereco", sensor.getAddress());
        context.setVariable("motivo", event.message());
        context.setVariable("volume", sensor.getVolume() == null ? "—" : String.format(Locale.ROOT, "%.0f L", sensor.getVolume()));
        context.setVariable("limite", String.format(Locale.ROOT, "%.0f L", sensor.getLimit()));
        context.setVariable("capacidade", String.format(Locale.ROOT, "%.0f L", sensor.getCapacity()));
        context.setVariable("percentual", sensor.getVolume() == null ? 0
                : (int) Math.min(100, Math.round(sensor.getVolume() / sensor.getCapacity() * 100)));
        context.setVariable("chamadosAbertos", openReports);
        context.setVariable("quando", DATE_TIME.format(event.at().atZone(BRAZIL)));
        context.setVariable("linkPainel", panelUrl("/painel/equipamentos/" + sensor.getId()));
        String html = templates.process("email/alerta-risco", context);

        deliver(activeRecipients(), subject, html, EVENT_MAX_RISK, String.valueOf(sensor.getId()));
    }

    /** E-mail de teste disparado pelo painel para conferir o SMTP antes da demonstração. */
    @Transactional
    public EmailLog sendTest(String to) {
        Context context = new Context(Locale.of("pt", "BR"));
        context.setVariable("quando", DATE_TIME.format(clock.instant().atZone(BRAZIL)));
        context.setVariable("linkPainel", panelUrl("/painel"));
        String html = templates.process("email/teste", context);
        List<EmailLog> result = deliver(Set.of(to.trim()), "[Saneia] Teste de envio de alertas", html, EVENT_TEST, null);
        return result.get(0);
    }

    public boolean smtpConfigured() {
        return mailSender.getIfAvailable() != null && properties.mail() != null && properties.mail().enabled();
    }

    Set<String> activeRecipients() {
        Set<String> emails = new LinkedHashSet<>();
        if (properties.mail() != null && properties.mail().recipients() != null) {
            properties.mail().recipients().stream().filter(StringUtils::hasText).map(String::trim).forEach(emails::add);
        }
        recipients.findByActiveTrue().forEach(r -> emails.add(r.getEmail()));
        return emails;
    }

    private List<EmailLog> deliver(Set<String> to, String subject, String html, String eventType, String referenceId) {
        Instant now = clock.instant();
        if (to.isEmpty()) {
            return List.of(logs.save(new EmailLog(null, subject, eventType, referenceId, EmailLog.Status.SKIPPED,
                    "Nenhum destinatário cadastrado em Alertas.", now)));
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || properties.mail() == null || !properties.mail().enabled()) {
            return to.stream().map(email -> logs.save(new EmailLog(email, subject, eventType, referenceId,
                    EmailLog.Status.SKIPPED, "SMTP não configurado (defina SPRING_MAIL_HOST e credenciais).", now)))
                    .toList();
        }
        return to.stream().map(email -> {
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(properties.mail().from());
                helper.setTo(email);
                helper.setSubject(subject);
                helper.setText(html, true);
                sender.send(message);
                log.info("E-mail '{}' enviado para {}", subject, email);
                return logs.save(new EmailLog(email, subject, eventType, referenceId, EmailLog.Status.SENT, null, now));
            } catch (MailException | MessagingException ex) {
                log.warn("Falha ao enviar e-mail para {}: {}", email, ex.getMessage());
                return logs.save(new EmailLog(email, subject, eventType, referenceId, EmailLog.Status.FAILED,
                        ex.getMessage(), now));
            }
        }).toList();
    }

    private String panelUrl(String path) {
        String base = StringUtils.hasText(properties.publicUrl()) ? properties.publicUrl().replaceAll("/+$", "")
                : "http://localhost:8080/api";
        return base + path;
    }
}
