package br.com.saneia.api.auth;

import java.time.Instant;

import br.com.saneia.api.user.UserResponse;

/** Session do contrato: token + user { id, name, email, role }. */
public record SessionResponse(String token, Instant expiresAt, UserResponse user) {
}
