package br.com.saneia.api.alert;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Registro de cada e-mail de alerta: enviado, com falha ou não enviado (SMTP desativado / sem destinatários). */
@Entity
@Table(name = "sn_email_logs", indexes = @Index(name = "ix_sn_email_logs_created", columnList = "created_at"))
public class EmailLog {

    public enum Status {
        SENT, FAILED, SKIPPED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 160)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "reference_id", length = 40)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private Status status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailLog() {
    }

    public EmailLog(String recipient, String subject, String eventType, String referenceId, Status status,
            String errorMessage, Instant createdAt) {
        this.recipient = recipient;
        this.subject = subject.length() > 200 ? subject.substring(0, 200) : subject;
        this.eventType = eventType;
        this.referenceId = referenceId;
        this.status = status;
        this.errorMessage = errorMessage == null || errorMessage.length() <= 500 ? errorMessage
                : errorMessage.substring(0, 500);
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getEventType() {
        return eventType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public Status getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
