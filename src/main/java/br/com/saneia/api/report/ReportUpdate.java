package br.com.saneia.api.report;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import br.com.saneia.api.file.StoredFile;
import br.com.saneia.api.user.AppUser;

/** Complemento aditivo de um protocolo em aberto (texto e/ou foto), sem criar outro protocolo. */
@Entity
@Table(name = "sn_report_updates")
public class ReportUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Column(name = "note", length = 600)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_file_id")
    private StoredFile photo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReportUpdate() {
    }

    public ReportUpdate(Report report, AppUser author, String note, StoredFile photo, Instant createdAt) {
        this.report = report;
        this.author = author;
        this.note = note;
        this.photo = photo;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Report getReport() {
        return report;
    }

    public AppUser getAuthor() {
        return author;
    }

    public String getNote() {
        return note;
    }

    public StoredFile getPhoto() {
        return photo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
