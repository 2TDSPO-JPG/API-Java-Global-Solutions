package br.com.saneia.api.sensor;

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

/**
 * Ocorrência com tipo, início e fim: alarme/alerta de nível ou perda de comunicação.
 * Leituras sucessivas da mesma condição atualizam o evento aberto, então um alarme contínuo conta uma vez.
 */
@Entity
@Table(name = "sn_sensor_events",
        indexes = @Index(name = "ix_sn_events_sensor_start", columnList = "sensor_id, started_at"))
public class SensorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_kind", nullable = false, length = 20)
    private EventKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private SensorStatus severity;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "event_message", nullable = false, length = 255)
    private String message;

    @Column(name = "peak_volume")
    private Double peakVolume;

    protected SensorEvent() {
    }

    public SensorEvent(Sensor sensor, EventKind kind, SensorStatus severity, Instant startedAt, String message,
            Double peakVolume) {
        this.sensor = sensor;
        this.kind = kind;
        this.severity = severity;
        this.startedAt = startedAt;
        this.message = message;
        this.peakVolume = peakVolume;
    }

    public void escalate(SensorStatus severity, String message) {
        this.severity = severity;
        this.message = message;
    }

    public void registerVolume(double volume) {
        if (peakVolume == null || volume > peakVolume) {
            peakVolume = volume;
        }
    }

    public void close(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Sensor getSensor() {
        return sensor;
    }

    public EventKind getKind() {
        return kind;
    }

    public SensorStatus getSeverity() {
        return severity;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getMessage() {
        return message;
    }

    public Double getPeakVolume() {
        return peakVolume;
    }
}
