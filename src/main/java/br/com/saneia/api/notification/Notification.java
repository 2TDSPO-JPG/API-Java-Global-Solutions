package br.com.saneia.api.notification;

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

import br.com.saneia.api.user.Role;

/**
 * Notificação gerada pelo consumidor assíncrono da fila saneia.notifications.
 * Vai para um usuário (recipientUserId) ou para todos de um papel (recipientRole), ex.: operadoras.
 */
@Entity
@Table(name = "sn_notifications", indexes = {
        @Index(name = "ix_sn_notif_user", columnList = "recipient_user_id, created_at"),
        @Index(name = "ix_sn_notif_role", columnList = "recipient_role, created_at")})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", length = 20)
    private Role recipientRole;

    @Column(name = "notification_type", nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "body", nullable = false, length = 600)
    private String message;

    @Column(name = "reference_id", length = 40)
    private String referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(Long recipientUserId, Role recipientRole, String type, String title, String message,
            String referenceId, Instant createdAt) {
        this.recipientUserId = recipientUserId;
        this.recipientRole = recipientRole;
        this.type = type;
        this.title = title;
        this.message = message.length() > 600 ? message.substring(0, 600) : message;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public Role getRecipientRole() {
        return recipientRole;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
