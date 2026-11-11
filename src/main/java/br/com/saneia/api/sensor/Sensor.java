package br.com.saneia.api.sensor;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Catálogo do equipamento + estado atual consolidado. As medições ficam em {@link SensorReading}
 * (tabela separada), então novas leituras não alteram os dados cadastrais.
 */
@Entity
@Table(name = "sn_sensors", indexes = @Index(name = "ix_sn_sensors_prefix", columnList = "prefix"))
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String prefix;

    @Column(name = "sensor_name", nullable = false, length = 100)
    private String name;

    @Column(name = "equipment_type", nullable = false, length = 100)
    private String equipmentType;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 80)
    private String neighborhood;

    @Embedded
    private SensorPostalAddress postalAddress;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "capacity_liters", nullable = false)
    private double capacity;

    @Column(name = "limit_liters", nullable = false)
    private double limitLiters;

    @Column(name = "radius_meters", nullable = false)
    private double radiusMeters;

    @Column(name = "expected_interval_min", nullable = false)
    private int expectedIntervalMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 20)
    private SensorStatus status = SensorStatus.OFFLINE;

    @Column(name = "current_volume")
    private Double volume;

    @Column(name = "current_level_cm")
    private Double levelCm;

    @Column(name = "last_reading_at")
    private Instant lastReadingAt;

    @Column(name = "status_reason", length = 255)
    private String reason;

    @Column(name = "normal_streak", nullable = false)
    private int normalStreak;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Sensor() {
    }

    public Sensor(Instant createdAt) {
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.reason = "Aguardando a primeira leitura do equipamento.";
    }

    public void updateCatalog(String prefix, String name, String equipmentType, String address, String neighborhood,
            double latitude, double longitude, double capacity, double limit, double radiusMeters,
            int expectedIntervalMinutes, Instant now) {
        this.prefix = prefix;
        this.name = name;
        this.equipmentType = equipmentType;
        this.address = address;
        this.neighborhood = neighborhood;
        this.latitude = latitude;
        this.longitude = longitude;
        this.capacity = capacity;
        this.limitLiters = limit;
        this.radiusMeters = radiusMeters;
        this.expectedIntervalMinutes = expectedIntervalMinutes;
        this.updatedAt = now;
    }

    /** Grava o endereço estruturado; nulo mantém o que já existe (edição de cadastro legado). */
    public void updatePostalAddress(PostalAddress address) {
        if (address != null) {
            this.postalAddress = SensorPostalAddress.of(address);
        }
    }

    public void retire(Instant now) {
        this.active = false;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public void applyState(SensorStatus status, String reason, Instant now) {
        this.status = status;
        this.reason = reason;
        this.updatedAt = now;
    }

    public void applyReading(double volume, Double levelCm, Instant measuredAt) {
        this.volume = volume;
        this.levelCm = levelCm;
        this.lastReadingAt = measuredAt;
    }

    public Long getId() {
        return id;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getName() {
        return name;
    }

    public String getEquipmentType() {
        return equipmentType;
    }

    public String getAddress() {
        return address;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public PostalAddress getPostalAddress() {
        return postalAddress == null ? null : postalAddress.toDto();
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getCapacity() {
        return capacity;
    }

    public double getLimit() {
        return limitLiters;
    }

    public double getRadiusMeters() {
        return radiusMeters;
    }

    public int getExpectedIntervalMinutes() {
        return expectedIntervalMinutes;
    }

    public SensorStatus getStatus() {
        return status;
    }

    public Double getVolume() {
        return volume;
    }

    public Double getLevelCm() {
        return levelCm;
    }

    public Instant getLastReadingAt() {
        return lastReadingAt;
    }

    public String getReason() {
        return reason;
    }

    public int getNormalStreak() {
        return normalStreak;
    }

    public void setNormalStreak(int normalStreak) {
        this.normalStreak = normalStreak;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
