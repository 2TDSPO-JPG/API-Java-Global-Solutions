package br.com.saneia.api.notification;

import java.time.Clock;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.saneia.api.auth.CurrentUser;
import br.com.saneia.api.common.ApiException;
import br.com.saneia.api.messaging.Events;
import br.com.saneia.api.user.Role;

/** Consome os eventos de relatos e sensores e grava as notificações de cada público. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSensorAlert(Events.SensorAlert event) {
        String title = switch (event.status()) {
            case "alarm" -> "Alarme de nível em " + event.prefix();
            case "offline" -> "Sem comunicação: " + event.prefix();
            default -> "Alerta prioritário em " + event.prefix();
        };
        save(null, Role.OPERATOR, "sensor_" + event.status().toLowerCase(Locale.ROOT), title,
                event.name() + ": " + event.message(), String.valueOf(event.sensorId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSensorNormalized(Events.SensorNormalized event) {
        save(null, Role.OPERATOR, "sensor_normalized", "Nível normalizado em " + event.prefix(),
                event.name() + " voltou à faixa normal após leituras estáveis.", String.valueOf(event.sensorId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReportOpened(Events.ReportOpened event) {
        String message = event.category() + " em " + event.neighborhood()
                + (event.recurrent() ? ". Recorrência: há protocolo anterior aberto no mesmo local." : ".");
        save(null, Role.OPERATOR, "report_opened", "Novo relato " + event.protocol(), message,
                String.valueOf(event.reportId()));
        save(event.ownerId(), null, "report_received", "Protocolo " + event.protocol() + " recebido",
                "Acompanhe o atendimento pelo app. Você será avisado a cada mudança.", String.valueOf(event.reportId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReportStatusChanged(Events.ReportStatusChanged event) {
        String message = switch (event.status()) {
            case "in_progress" -> "A operadora iniciou o atendimento."
                    + (event.progressNote() == null ? "" : " " + event.progressNote());
            case "closed" -> "Atendimento encerrado. Desfecho: " + event.resolution();
            default -> event.progressNote() == null ? "O protocolo foi atualizado." : event.progressNote();
        };
        save(event.ownerId(), null, "report_" + event.status(), "Protocolo " + event.protocol(), message,
                String.valueOf(event.reportId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAssessmentRequested(Events.AssessmentRequested event) {
        save(null, Role.OPERATOR, "assessment_requested", "Avaliação técnica: " + event.protocol(), event.note(),
                String.valueOf(event.reportId()));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(CurrentUser user, Pageable pageable) {
        return notifications.findForRecipient(user.id(), user.role(), pageable).map(NotificationResponse::from);
    }

    @Transactional
    public NotificationResponse markRead(CurrentUser user, Long id) {
        Notification notification = notifications.findForRecipient(id, user.id(), user.role())
                .orElseThrow(() -> ApiException.notFound("Notificação não encontrada."));
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification);
    }

    private void save(Long userId, Role role, String type, String title, String message, String referenceId) {
        notifications.save(new Notification(userId, role, type, title, message, referenceId, clock.instant()));
        log.info("Notificação gerada ({}) para {}: {}", type, userId != null ? "usuário " + userId : role, title);
    }
}
