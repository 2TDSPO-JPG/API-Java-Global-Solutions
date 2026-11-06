package br.com.saneia.api.user;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.saneia.api.common.PageResponses;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administração", description = "Usuários e catálogo de equipamentos (papel admin)")
public class AdminUserController {

    private final AppUserRepository users;

    public AdminUserController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "Lista usuários (id, nome, e-mail e papel)", description = "Paginado: page (0..) e size (1..200).")
    public ResponseEntity<List<UserResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        return PageResponses.of(users.findAllByOrderByIdAsc(request), UserResponse::from);
    }
}
