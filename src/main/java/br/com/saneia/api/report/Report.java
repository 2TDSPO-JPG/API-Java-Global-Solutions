package br.com.saneia.api.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import br.com.saneia.api.file.StoredFile;
import br.com.saneia.api.sensor.Sensor;
import br.com.saneia.api.user.AppUser;

/** Relato do morador (protocolo). Relaciona-se a usuário, sensor sugerido, foto, relato anterior e complementos. */
@Entity
@Table(name = "sn_reports", indexes = {
        @Index(name = "ix_sn_reports_owner", columnList = "owner_id, created_at"),
        @Index(name = "ix_sn_reports_status", columnList = "report_status")})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String protocol;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 80)
    private String neighborhood;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", nullable = false, length = 20)
    private ReportStatus status = ReportStatus.RECEIVED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_id")
    private Sensor sensor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_file_id")
    private StoredFile photo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_report_id")
    private Report previousReport;

    @Column(length = 600)
    private String resolution;

    @Column(name = "progress_note", length = 600)
    private String progressNote;

    @Column(name = "assessment_note", length = 600)
    private String assessmentNote;

    @Column(name = "assessment_requested_at")
    private Instant assessmentRequestedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_level", length = 20)
    private ReportPriority priority;

    @Column(name = "due_at")
    private Instant dueAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "closing_outcome", length = 20)
    private ReportOutcome outcome;

    @Column(name = "reopen_count")
    private Integer reopenCount;

    /** Vínculo com o sensor definido manualmente pela equipe (não é recalculado pela cobertura ao editar). */
    @Column(name = "sensor_locked")
    private Boolean sensorLocked;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<ReportUpdate> updates = new ArrayList<>();

    protected Report() {
    }

    public Report(String protocol, AppUser owner, Instant createdAt) {
        this.protocol = protocol;
        this.owner = owner;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void applyContent(String category, String description, String address, String neighborhood,
            double latitude, double longitude, Instant now) {
        this.category = category;
        this.description = description;
        this.address = address;
        this.neighborhood = neighborhood;
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = now;
    }

    public void linkSensor(Sensor sensor) {
        this.sensor = sensor;
    }

    public void linkPrevious(Report previous) {
        this.previousReport = previous;
    }

    public void replacePhoto(StoredFile photo) {
        this.photo = photo;
    }

    public void changeStatus(ReportStatus status, String resolution, String progressNote, Instant now) {
        this.status = status;
        if (resolution != null) {
            this.resolution = resolution;
        }
        if (progressNote != null) {
            this.progressNote = progressNote;
        }
        if (status == ReportStatus.CLOSED && closedAt == null) {
            this.closedAt = now;
        }
        this.updatedAt = now;
    }

    public void requestAssessment(String note, Instant now) {
        this.assessmentNote = note;
        this.assessmentRequestedAt = now;
        this.updatedAt = now;
    }

    /** Exclusão lógica: some das listas, mas os dados mínimos da abertura continuam valendo para a regra de 20 dias. */
    public void markDeleted(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    /** Define a prioridade e recalcula o prazo (SLA) a partir da abertura. */
    public void prioritize(ReportPriority priority, Instant now) {
        this.priority = priority;
        this.dueAt = createdAt.plus(priority.sla());
        this.updatedAt = now;
    }

    public void assign(AppUser assignee, Instant now) {
        this.assignee = assignee;
        this.updatedAt = now;
    }

    public void lockSensor(Sensor sensor, boolean locked) {
        this.sensor = sensor;
        this.sensorLocked = locked;
    }

    public void close(ReportOutcome outcome) {
        this.outcome = outcome == null ? ReportOutcome.RESOLVED : outcome;
    }

    /** Reabre um protocolo encerrado: volta para em atendimento e limpa o encerramento. */
    public void reopen(String progressNote, Instant now) {
        this.status = ReportStatus.IN_PROGRESS;
        this.closedAt = null;
        this.outcome = null;
        this.progressNote = progressNote;
        this.reopenCount = getReopenCount() + 1;
        this.updatedAt = now;
    }

    public boolean isOverdue(Instant now) {
        return isOpen() && !isDeleted() && dueAt != null && now.isAfter(dueAt);
    }

    public void addUpdate(ReportUpdate update) {
        updates.add(update);
        this.updatedAt = update.getCreatedAt();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isOpen() {
        return status != ReportStatus.CLOSED;
    }

    public Long getId() {
        return id;
    }

    public String getProtocol() {
        return protocol;
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getAddress() {
        return address;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Sensor getSensor() {
        return sensor;
    }

    public StoredFile getPhoto() {
        return photo;
    }

    public Report getPreviousReport() {
        return previousReport;
    }

    public String getResolution() {
        return resolution;
    }

    public String getProgressNote() {
        return progressNote;
    }

    public String getAssessmentNote() {
        return assessmentNote;
    }

    public Instant getAssessmentRequestedAt() {
        return assessmentRequestedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public List<ReportUpdate> getUpdates() {
        return updates;
    }

    public ReportPriority getPriority() {
        return priority == null ? ReportPriority.MEDIUM : priority;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public AppUser getAssignee() {
        return assignee;
    }

    public ReportOutcome getOutcome() {
        return outcome;
    }

    public int getReopenCount() {
        return reopenCount == null ? 0 : reopenCount;
    }

    public boolean isSensorLocked() {
        return Boolean.TRUE.equals(sensorLocked);
    }
}
