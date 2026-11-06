package br.com.saneia.api.notification;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.saneia.api.auth.CurrentUser;
import br.com.saneia.api.common.PageResponses;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notificações", description = "Avisos gerados de forma assíncrona pela fila saneia.notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "Notificações do usuário logado (e do seu papel)", description = "Paginado: page e size.")
    public ResponseEntity<List<NotificationResponse>> list(Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PageResponses.of(notifications.list(CurrentUser.from(auth), request), Function.identity());
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marca a notificação como lida")
    public NotificationResponse markRead(Authentication auth, @PathVariable Long id) {
        return notifications.markRead(CurrentUser.from(auth), id);
    }
}
