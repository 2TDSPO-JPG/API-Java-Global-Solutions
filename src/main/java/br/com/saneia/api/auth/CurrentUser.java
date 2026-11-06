package br.com.saneia.api.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import br.com.saneia.api.user.Role;

/** Identidade extraída do JWT validado: id do usuário (subject) e papel (claim role). */
public record CurrentUser(Long id, Role role) {

    public static CurrentUser from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Requisição sem usuário autenticado.");
        }
        return new CurrentUser(Long.valueOf(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString("role")));
    }

    public boolean is(Role expected) {
        return role == expected;
    }
}
