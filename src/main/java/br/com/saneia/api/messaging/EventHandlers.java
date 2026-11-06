package br.com.saneia.api.messaging;

import org.springframework.stereotype.Component;

import br.com.saneia.api.notification.NotificationService;
import br.com.saneia.api.telemetry.TelemetryProcessor;

/** Roteia cada evento consumido para o serviço responsável (usado pelos listeners e pelo modo inline). */
@Component
public class EventHandlers {

    private final TelemetryProcessor telemetry;
    private final NotificationService notifications;

    public EventHandlers(TelemetryProcessor telemetry, NotificationService notifications) {
        this.telemetry = telemetry;
        this.notifications = notifications;
    }

    public void handle(Object event) {
        if (event instanceof Events.ReadingReceived reading) {
            telemetry.process(reading);
        } else if (event instanceof Events.SensorAlert alert) {
            notifications.onSensorAlert(alert);
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
