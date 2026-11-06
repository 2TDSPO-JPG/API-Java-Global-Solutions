package br.com.saneia.api.notification;

import java.time.Instant;

public record NotificationResponse(String id, String type, String title, String message, String referenceId,
        Instant createdAt, Instant readAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(String.valueOf(n.getId()), n.getType(), n.getTitle(), n.getMessage(),
                n.getReferenceId(), n.getCreatedAt(), n.getReadAt());
    }
}
