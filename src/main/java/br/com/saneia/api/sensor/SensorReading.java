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

/** Uma linha por medição válida (inclusive as normais), com capacidade e limite vigentes na leitura. */
@Entity
@Table(name = "sn_sensor_readings",
        indexes = @Index(name = "ix_sn_readings_sensor_time", columnList = "sensor_id, measured_at"))
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "volume_liters", nullable = false)
    private double volume;

    @Column(name = "level_cm")
    private Double levelCm;

    @Column(name = "capacity_liters", nullable = false)
    private double capacity;

    @Column(name = "limit_liters", nullable = false)
    private double limitLiters;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_status", nullable = false, length = 20)
    private SensorStatus status;

    @Column(name = "reading_source", length = 60)
    private String source;

    @Column(length = 255)
    private String note;

    protected SensorReading() {
    }

    public SensorReading(Sensor sensor, Instant measuredAt, Instant receivedAt, double volume, Double levelCm,
            SensorStatus status, String source, String note) {
        this.sensor = sensor;
        this.measuredAt = measuredAt;
        this.receivedAt = receivedAt;
        this.volume = volume;
        this.levelCm = levelCm;
        this.capacity = sensor.getCapacity();
        this.limitLiters = sensor.getLimit();
        this.status = status;
        this.source = source;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Sensor getSensor() {
        return sensor;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public double getVolume() {
        return volume;
    }

    public Double getLevelCm() {
        return levelCm;
    }

    public double getCapacity() {
        return capacity;
    }

    public double getLimit() {
        return limitLiters;
    }

    public SensorStatus getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public String getNote() {
        return note;
    }
}
