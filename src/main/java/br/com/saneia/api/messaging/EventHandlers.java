package br.com.saneia.api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.saneia.api.alert.AlertEmailService;
import br.com.saneia.api.notification.NotificationService;
import br.com.saneia.api.telemetry.TelemetryProcessor;

/** Roteia cada evento consumido para o serviço responsável (usado pelos listeners e pelo modo inline). */
@Component
public class EventHandlers {

    private static final Logger log = LoggerFactory.getLogger(EventHandlers.class);

    private final TelemetryProcessor telemetry;
    private final NotificationService notifications;
    private final AlertEmailService alertEmails;

    public EventHandlers(TelemetryProcessor telemetry, NotificationService notifications, AlertEmailService alertEmails) {
        this.telemetry = telemetry;
        this.notifications = notifications;
        this.alertEmails = alertEmails;
    }

    public void handle(Object event) {
        if (event instanceof Events.ReadingReceived reading) {
            telemetry.process(reading);
        } else if (event instanceof Events.SensorAlert alert) {
            notifications.onSensorAlert(alert);
            try {
                alertEmails.onSensorAlert(alert); // risco máximo → e-mail para os destinatários de alerta
            } catch (RuntimeException ex) {
                log.error("Falha ao preparar o e-mail de risco máximo do sensor {}", alert.sensorId(), ex);
            }
        } else if (event instanceof Events.SensorNormalized normalized) {
            notifications.onSensorNormalized(normalized);
        } else if (event instanceof Events.ReportOpened opened) {
            notifications.onReportOpened(opened);
        } else if (event instanceof Events.ReportStatusChanged changed) {
            notifications.onReportStatusChanged(changed);
        } else if (event instanceof Events.AssessmentRequested assessment) {
            notifications.onAssessmentRequested(assessment);
        }
    }
}
