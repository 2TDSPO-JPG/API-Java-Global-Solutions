package br.com.saneia.api.file;

import java.time.Instant;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** Foto anexada a um relato ou complemento. O banco guarda a referência e o conteúdo binário. */
@Entity
@Table(name = "sn_files")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_name", nullable = false, length = 160)
    private String fileName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StoredFile() {
    }

    public StoredFile(String contentType, String fileName, byte[] content, Instant createdAt) {
        this.contentType = contentType;
        this.fileName = fileName;
        this.content = content;
        this.sizeBytes = content.length;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public byte[] getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
