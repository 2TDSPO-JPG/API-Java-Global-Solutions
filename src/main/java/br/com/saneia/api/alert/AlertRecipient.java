package br.com.saneia.api.alert;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Pessoa que recebe por e-mail os alertas de risco máximo (cadastrada pelo admin no painel). */
@Entity
@Table(name = "sn_alert_recipients")
public class AlertRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AlertRecipient() {
    }

    public AlertRecipient(String name, String email, Instant createdAt) {
        this.name = name;
        this.email = email;
        this.createdAt = createdAt;
    }

    public void toggle() {
        this.active = !this.active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
