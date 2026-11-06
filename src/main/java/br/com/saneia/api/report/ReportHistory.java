package br.com.saneia.api.report;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Trilha de auditoria do chamado: quem fez o quê e quando (status, edição, atribuição, notas internas...). */
@Entity
@Table(name = "sn_report_history", indexes = @Index(name = "ix_sn_history_report", columnList = "report_id, created_at"))
public class ReportHistory {

    public enum Type {
        OPENED, EDITED, STATUS, PRIORITY, ASSIGNED, SENSOR_LINK, REOPENED, AUTO_REOPENED, NOTE, UPDATE, ASSESSMENT,
        DELETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private Type type;

    @Column(name = "actor_name", nullable = false, length = 120)
    private String actorName;

    @Column(name = "from_value", length = 80)
    private String fromValue;

    @Column(name = "to_value", length = 80)
    private String toValue;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "internal_only", nullable = false)
    private boolean internalOnly;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReportHistory() {
    }

    public ReportHistory(Report report, Type type, String actorName, String fromValue, String toValue, String message,
            boolean internalOnly, Instant createdAt) {
        this.report = report;
        this.type = type;
        this.actorName = actorName == null ? "Sistema" : truncate(actorName, 120);
        this.fromValue = truncate(fromValue, 80);
        this.toValue = truncate(toValue, 80);
        this.message = truncate(message, 1000);
        this.internalOnly = internalOnly;
        this.createdAt = createdAt;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Report getReport() {
        return report;
    }

    public Type getType() {
        return type;
    }

    public String getActorName() {
        return actorName;
    }

    public String getFromValue() {
        return fromValue;
    }

    public String getToValue() {
        return toValue;
    }

    public String getMessage() {
        return message;
    }

    public boolean isInternalOnly() {
        return internalOnly;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
